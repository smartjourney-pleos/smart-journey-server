package com.smartjourney.server.journey;

import static com.smartjourney.server.support.ApiFixture.STORE_A;
import static com.smartjourney.server.support.ApiFixture.STORE_B;
import static com.smartjourney.server.support.ApiFixture.device;
import static com.smartjourney.server.support.ApiFixture.json;
import static com.smartjourney.server.support.ApiFixture.pos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.support.ApiFixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 1 핵심 루프: 여정 → 접수(멱등·동시성) → POS 보드 → 데모 패널 → 취소.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JourneyWaitingFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    ApiFixture api;
    String posA;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiFixture(mvc, om);
        posA = api.posToken(STORE_A);
        api.resetStore(posA);
        api.resetStore(api.posToken(STORE_B));
    }

    @Test
    @DisplayName("1-1 여정: 생성 → current 복구 → 인원 변경 → 기기당 ACTIVE 1개 → 종료 후 204")
    void journeyLifecycle() throws Exception {
        String dev = api.newDevice();
        mvc.perform(device(get("/api/v1/journeys/current"), dev)).andExpect(status().isNoContent());

        long journeyId = api.startJourney(dev, 4, STORE_A, STORE_B);

        mvc.perform(device(get("/api/v1/journeys/current"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journeyId").value(journeyId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stops[0].storeName").value("42dot"))
                .andExpect(jsonPath("$.stops[1].seq").value(2))
                .andExpect(jsonPath("$.stops[1].status").value("PLANNED"));

        mvc.perform(device(json(patch("/api/v1/journeys/" + journeyId + "/party"),
                        "{\"partySize\":3,\"partySource\":\"MANUAL\"}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partySize").value(3))
                .andExpect(jsonPath("$.partySource").value("MANUAL"));

        mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[1]}"), dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_JOURNEY_EXISTS"));

        mvc.perform(device(post("/api/v1/journeys/" + journeyId + "/end"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.stops[0].status").value("SKIPPED"));
        mvc.perform(device(get("/api/v1/journeys/current"), dev)).andExpect(status().isNoContent());

        // 남의 여정은 안 보인다
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), api.newDevice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOURNEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("1-1 여정: 4곳 → 400 TOO_MANY_STOPS / 같은 매장 두 번 → 400 / 없는 매장 → 404")
    void journeyValidation() throws Exception {
        String dev = api.newDevice();
        mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[1,2,3,4]}"), dev))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_STOPS"));
        mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[1,1]}"), dev))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[999]}"), dev))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":10,\"partySource\":\"SENSOR\",\"storeIds\":[1]}"), dev))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("1-2 접수: 같은 키 → 같은 웨이팅 / 같은 키 다른 바디 → 409 / 키 없음 → 400 / 중복 접수 → 409")
    void idempotentRegistration() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 4, STORE_A, STORE_B);

        JsonNode first = api.read(api.registerRaw(dev, "card-0001", journeyId, STORE_A, 4, "TOUCH")
                .andExpect(status().isCreated()));
        assertThat(first.get("ticketNo").asInt()).isPositive();
        assertThat(first.get("status").asText()).isEqualTo("REGISTERED");
        assertThat(first.get("storeName").asText()).isEqualTo("42dot");

        // 화면 승인과 음성 승인이 같은 키로 → 하나만
        JsonNode again = api.read(api.registerRaw(dev, "card-0001", journeyId, STORE_A, 4, "VOICE")
                .andExpect(status().isCreated()));
        assertThat(again.get("waitingId").asLong()).isEqualTo(first.get("waitingId").asLong());
        assertThat(again.get("ticketNo").asInt()).isEqualTo(first.get("ticketNo").asInt());

        api.registerRaw(dev, "card-0001", journeyId, STORE_A, 3, "TOUCH")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        api.registerRaw(dev, null, journeyId, STORE_A, 4, "TOUCH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        api.registerRaw(dev, "card-0002", journeyId, STORE_A, 4, "TOUCH")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_WAITING"));

        // 여정 현황에 웨이팅이 붙는다
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), dev))
                .andExpect(jsonPath("$.stops[0].status").value("WAITING"))
                .andExpect(jsonPath("$.stops[0].waiting.waitingId").value(first.get("waitingId").asLong()))
                .andExpect(jsonPath("$.stops[0].waiting.aheadTeams").value(0));
    }

    @Test
    @DisplayName("1-2 접수: 코스에 없는 매장 → 400, 남의 여정 → 404")
    void registrationGuards() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A);
        api.registerRaw(dev, "card-x-0001", journeyId, STORE_B, 2, "TOUCH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        api.registerRaw(api.newDevice(), "card-x-0002", journeyId, STORE_A, 2, "TOUCH")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOURNEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("1-2 동시 접수 8건 → 번호표 중복 없음 / 같은 키 동시 4건 → 웨이팅 1개")
    void concurrentRegistration() throws Exception {
        int n = 8;
        List<String> devices = new ArrayList<>();
        List<Long> journeys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String dev = api.newDevice();
            devices.add(dev);
            journeys.add(api.startJourney(dev, 2, STORE_A));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(pool.submit(() -> api.register(devices.get(idx), journeys.get(idx), STORE_A, 2)));
            }
            Set<Integer> tickets = new HashSet<>();
            for (Future<JsonNode> f : futures) {
                tickets.add(f.get().get("ticketNo").asInt());
            }
            assertThat(tickets).hasSize(n);

            String dev = api.newDevice();
            long journeyId = api.startJourney(dev, 3, STORE_B);
            List<Callable<JsonNode>> sameKey = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                sameKey.add(() -> api.read(api.registerRaw(dev, "same-key-0001", journeyId, STORE_B, 3,
                        "VOICE").andExpect(status().isCreated())));
            }
            Set<Long> ids = new HashSet<>();
            for (Future<JsonNode> f : pool.invokeAll(sameKey)) {
                ids.add(f.get().get("waitingId").asLong());
            }
            assertThat(ids).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("1-5·1-6 POS 보드 순서·차량 표시 / 데모 +3 → 대기 15분 증가 / −1")
    void posBoardAndDemoPanel() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 4, STORE_A);
        long waitingId = api.register(dev, journeyId, STORE_A, 4).get("waitingId").asLong();

        mvc.perform(pos(post("/api/v1/pos/demo/walk-ins"), posA).contentType("application/json")
                        .content("{\"count\":3,\"partySize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedTeams").value(3))
                .andExpect(jsonPath("$.aheadTeams").value(4))
                .andExpect(jsonPath("$.estimatedWaitMin").value(20));

        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aheadTeams").value(4))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].waitingId").value(waitingId))
                .andExpect(jsonPath("$.items[0].channel").value("VEHICLE"))
                .andExpect(jsonPath("$.items[0].vehicleVerified").value(true))
                .andExpect(jsonPath("$.items[1].channel").value("WALK_IN"))
                .andExpect(jsonPath("$.items[1].vehicleVerified").value(false));

        // 차량이 보는 매장 대기 현황 (새로 줄 서는 팀 기준)
        mvc.perform(device(get("/api/v1/stores/" + STORE_A + "/queue-status"), dev))
                .andExpect(jsonPath("$.aheadTeams").value(4))
                .andExpect(jsonPath("$.estimatedWaitMin").value(20));

        mvc.perform(pos(delete("/api/v1/pos/demo/walk-ins?count=1"), posA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedTeams").value(1))
                .andExpect(jsonPath("$.aheadTeams").value(3));

        // 맨 앞 차량 팀은 앞에 아무도 없다
        mvc.perform(device(get("/api/v1/waitings/" + waitingId), dev))
                .andExpect(jsonPath("$.aheadTeams").value(0))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));
    }

    @Test
    @DisplayName("1-3 취소 → POS 보드에서 빠지고, 마지막 매장이면 여정도 끝난다 / 남의 웨이팅 → 404")
    void cancel() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A);
        long waitingId = api.register(dev, journeyId, STORE_A, 2).get("waitingId").asLong();

        mvc.perform(device(delete("/api/v1/waitings/" + waitingId), api.newDevice()))
                .andExpect(status().isNotFound());

        mvc.perform(device(delete("/api/v1/waitings/" + waitingId), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.aheadTeams").value(0));

        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), dev))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.stops[0].status").value("DONE"));
        mvc.perform(device(delete("/api/v1/waitings/" + waitingId), dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("여정 끝내기 → 줄 선 웨이팅은 취소, 웨이팅 전 매장은 SKIPPED")
    void endJourneyCancelsWaiting() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A, STORE_B);
        long waitingId = api.register(dev, journeyId, STORE_A, 2).get("waitingId").asLong();

        mvc.perform(device(post("/api/v1/journeys/" + journeyId + "/end"), dev))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.stops[0].status").value("DONE"))
                .andExpect(jsonPath("$.stops[0].waiting.status").value("CANCELLED"))
                .andExpect(jsonPath("$.stops[1].status").value("SKIPPED"));
        mvc.perform(device(get("/api/v1/waitings/" + waitingId), dev))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
