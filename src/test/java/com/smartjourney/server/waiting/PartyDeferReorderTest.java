package com.smartjourney.server.waiting;

import static com.smartjourney.server.support.ApiFixture.STORE_A;
import static com.smartjourney.server.support.ApiFixture.STORE_B;
import static com.smartjourney.server.support.ApiFixture.device;
import static com.smartjourney.server.support.ApiFixture.json;
import static com.smartjourney.server.support.ApiFixture.pos;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.support.ApiFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 3·4: 인원 변경(T10) · 순번 미루기 · 코스 순서 변경 · 동의 철회 정책 · 데모 초기화.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PartyDeferReorderTest {

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
    }

    @Test
    @DisplayName("3-1 인원 변경 4 → 3: POS 카드·이벤트 기록 반영 / 범위 밖 400")
    void changePartySize() throws Exception {
        String dev = api.newDevice();
        long id = api.register(dev, api.startJourney(dev, 4, STORE_A), STORE_A, 4).get("waitingId").asLong();

        mvc.perform(device(json(patch("/api/v1/waitings/" + id + "/party-size"), "{\"partySize\":3}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partySize").value(3));
        mvc.perform(device(json(patch("/api/v1/waitings/" + id + "/party-size"), "{\"partySize\":0}"), dev))
                .andExpect(status().isBadRequest());

        mvc.perform(pos(get("/api/v1/pos/waitings/" + id), posA))
                .andExpect(jsonPath("$.item.partySize").value(3))
                .andExpect(jsonPath("$.events[1].type").value("PARTY_SIZE_CHANGED"))
                .andExpect(jsonPath("$.events[1].payload").value("{\"from\":4,\"to\":3}"));
    }

    @Test
    @DisplayName("4-5 순번 미루기: 10분 늦음(팀당 5분) → 2팀 뒤로 / 2회 초과 409 / 뒤에 아무도 없으면 횟수 안 씀")
    void defer() throws Exception {
        String dev = api.newDevice();
        long id = api.register(dev, api.startJourney(dev, 2, STORE_A), STORE_A, 2).get("waitingId").asLong();

        // 뒤에 아무도 없음
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/defer"), "{\"delayMin\":10}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deferCount").value(0))
                .andExpect(jsonPath("$.aheadTeams").value(0));

        mvc.perform(pos(json(post("/api/v1/pos/demo/walk-ins"), "{\"count\":3,\"partySize\":2}"), posA))
                .andExpect(status().isOk());

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/defer"), "{\"delayMin\":10}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deferCount").value(1))
                .andExpect(jsonPath("$.maxDefers").value(2))
                .andExpect(jsonPath("$.aheadTeams").value(2))
                .andExpect(jsonPath("$.estimatedWaitMin").value(10));

        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items[2].waitingId").value(id))
                .andExpect(jsonPath("$.items[2].deferCount").value(1));

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/defer"), "{\"delayMin\":1}"), dev))
                .andExpect(jsonPath("$.deferCount").value(2))
                .andExpect(jsonPath("$.aheadTeams").value(3));

        mvc.perform(pos(json(post("/api/v1/pos/demo/walk-ins"), "{\"count\":1,\"partySize\":2}"), posA));
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/defer"), "{\"delayMin\":5}"), dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEFER_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("도착한 팀은 미루기 불가 (REGISTERED 에서만)")
    void deferOnlyBeforeArrival() throws Exception {
        String dev = api.newDevice();
        long id = api.register(dev, api.startJourney(dev, 2, STORE_A), STORE_A, 2).get("waitingId").asLong();
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                "{\"result\":\"MANUAL\",\"partySize\":2}"), dev)).andExpect(status().isOk());
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/defer"), "{\"delayMin\":10}"), dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("4-5 코스 순서 변경 A,B → B,A / 다른 매장 섞이면 400")
    void reorderStops() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A, STORE_B);

        mvc.perform(device(json(put("/api/v1/journeys/" + journeyId + "/stops"), "{\"storeIds\":[2,1]}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].storeId").value(STORE_B))
                .andExpect(jsonPath("$.stops[0].seq").value(1))
                .andExpect(jsonPath("$.stops[1].storeId").value(STORE_A))
                .andExpect(jsonPath("$.stops[1].seq").value(2));
        mvc.perform(device(get("/api/v1/journeys/current"), dev))
                .andExpect(jsonPath("$.stops[0].storeName").value("소프트웨어드림센터"));

        mvc.perform(device(json(put("/api/v1/journeys/" + journeyId + "/stops"), "{\"storeIds\":[1]}"), dev))
                .andExpect(status().isBadRequest());
        mvc.perform(device(json(put("/api/v1/journeys/" + journeyId + "/stops"), "{\"storeIds\":[1,999]}"), dev))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3-6 동의 철회 → 진행 중 여정 종료 + 줄 선 웨이팅 취소(매장 보드에서 빠짐)")
    void revokeCancelsWaiting() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A, STORE_B);
        long id = api.register(dev, journeyId, STORE_A, 2).get("waitingId").asLong();

        mvc.perform(device(delete("/api/v1/devices/me/consent"), dev)).andExpect(status().isOk());

        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(pos(get("/api/v1/pos/waitings/" + id), posA))
                .andExpect(jsonPath("$.item.status").value("CANCELLED"))
                .andExpect(jsonPath("$.events[1].payload").value("{\"by\":\"CONSENT_REVOKED\"}"));
        // 철회한 기기는 아무것도 못 보냄
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), dev))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("4-6 데모 초기화: 줄 선 팀 CANCELLED, 호출된 팀 NO_SHOW, 대기 0팀")
    void demoReset() throws Exception {
        String dev = api.newDevice();
        long id = api.register(dev, api.startJourney(dev, 2, STORE_A), STORE_A, 2).get("waitingId").asLong();
        mvc.perform(pos(json(post("/api/v1/pos/demo/walk-ins"), "{\"count\":2,\"partySize\":2}"), posA));
        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/call"), posA)).andExpect(status().isOk());

        mvc.perform(pos(post("/api/v1/pos/demo/reset"), posA))
                .andExpect(jsonPath("$.changedTeams").value(3))
                .andExpect(jsonPath("$.aheadTeams").value(0))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));
        mvc.perform(device(get("/api/v1/waitings/" + id), dev))
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }
}
