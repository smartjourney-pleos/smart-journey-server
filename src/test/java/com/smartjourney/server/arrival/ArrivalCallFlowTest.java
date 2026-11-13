package com.smartjourney.server.arrival;

import static com.smartjourney.server.support.ApiFixture.STORE_A;
import static com.smartjourney.server.support.ApiFixture.STORE_B;
import static com.smartjourney.server.support.ApiFixture.device;
import static com.smartjourney.server.support.ApiFixture.json;
import static com.smartjourney.server.support.ApiFixture.pos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Phase 2: ETA → 도착·하차 확인 → 호출(QR) → 착석/노쇼. E2E T1 정상 흐름 · T6 하차 실패→수동.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArrivalCallFlowTest {

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
    @DisplayName("T1 정상 흐름: 접수 → ETA → 3/4명 하차(CONFIRMED) → 호출·QR → 입장 확인 → 착석 → 여정 다음 매장")
    void happyPath() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 4, STORE_A, STORE_B);
        long id = api.register(dev, journeyId, STORE_A, 4).get("waitingId").asLong();

        mvc.perform(device(json(put("/api/v1/waitings/" + id + "/eta"), "{\"etaMin\":7}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etaMin").value(7));
        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items[0].etaMin").value(7));

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"CONFIRMED\",\"alighted\":3,\"partySize\":4,"
                                + "\"evidence\":{\"occupancyBefore\":4,\"occupancyAfter\":1}}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingStatus").value("ARRIVED"))
                .andExpect(jsonPath("$.alighted").value(3));

        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items[0].status").value("ARRIVED"))
                .andExpect(jsonPath("$.items[0].arrival.result").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].arrival.alighted").value(3))
                .andExpect(jsonPath("$.items[0].arrival.partySize").value(4));

        // 호출 전에는 번호표 QR 없음
        mvc.perform(device(get("/api/v1/waitings/" + id + "/ticket"), dev))
                .andExpect(status().isConflict());

        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/call"), posA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("CALLED"));

        JsonNode ticket = api.read(mvc.perform(device(get("/api/v1/waitings/" + id + "/ticket"), dev))
                .andExpect(status().isOk()));
        String qr = ticket.get("qrPayload").asText();
        assertThat(qr).startsWith("SJ1.");

        mvc.perform(pos(json(post("/api/v1/pos/tickets/verify"), "{\"qrPayload\":\"" + qr + "\"}"), posA))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.waitingId").value(id))
                .andExpect(jsonPath("$.partySize").value(4));
        // 다른 매장 POS 에서는 무효
        mvc.perform(pos(json(post("/api/v1/pos/tickets/verify"), "{\"qrPayload\":\"" + qr + "\"}"),
                        api.posToken(STORE_B)))
                .andExpect(jsonPath("$.valid").value(false));

        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/seat"), posA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("SEATED"));

        // 착석 후 QR 은 다시 못 씀, 보드에서 빠짐
        mvc.perform(pos(json(post("/api/v1/pos/tickets/verify"), "{\"qrPayload\":\"" + qr + "\"}"), posA))
                .andExpect(jsonPath("$.valid").value(false));
        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items.length()").value(0));

        // 팀 상세 이벤트 기록 (P-03)
        mvc.perform(pos(get("/api/v1/pos/waitings/" + id), posA))
                .andExpect(jsonPath("$.events[0].type").value("REGISTERED"))
                .andExpect(jsonPath("$.events[1].type").value("ETA_UPDATED"))
                .andExpect(jsonPath("$.events[2].type").value("ARRIVAL_REPORTED"))
                .andExpect(jsonPath("$.events[3].type").value("CALLED"))
                .andExpect(jsonPath("$.events[4].type").value("SEATED"));

        // A 끝 → 여정은 B 가 남아 있어 계속
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), dev))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stops[0].status").value("DONE"))
                .andExpect(jsonPath("$.stops[1].status").value("PLANNED"));
    }

    @Test
    @DisplayName("T6 하차 실패 → UNCONFIRMED(노랑) 뒤 MANUAL(회색)로 갱신 / 잘못된 값 400")
    void unconfirmedThenManual() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_A);
        long id = api.register(dev, journeyId, STORE_A, 2).get("waitingId").asLong();

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"UNCONFIRMED\",\"alighted\":1,\"partySize\":2}"), dev))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARRIVAL_INVALID"));
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"CONFIRMED\",\"alighted\":0,\"partySize\":2}"), dev))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARRIVAL_INVALID"));

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"UNCONFIRMED\",\"partySize\":2}"), dev))
                .andExpect(status().isOk());
        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items[0].arrival.result").value("UNCONFIRMED"));

        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"MANUAL\",\"partySize\":2}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingStatus").value("ARRIVED"));
        mvc.perform(pos(get("/api/v1/pos/queue"), posA))
                .andExpect(jsonPath("$.items[0].arrival.result").value("MANUAL"));
    }

    @Test
    @DisplayName("2-3 도착 전 호출 → 도착 보고해도 CALLED 유지 / 호출 후 인원 변경 409 / 노쇼")
    void calledBeforeArrival() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 3, STORE_A);
        long id = api.register(dev, journeyId, STORE_A, 3).get("waitingId").asLong();

        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/call"), posA)).andExpect(status().isOk());
        mvc.perform(device(json(post("/api/v1/waitings/" + id + "/arrival"),
                        "{\"result\":\"CONFIRMED\",\"alighted\":3,\"partySize\":3}"), dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingStatus").value("CALLED"));

        mvc.perform(device(json(patch("/api/v1/waitings/" + id + "/party-size"), "{\"partySize\":2}"), dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITING_ALREADY_CALLED"));

        // 호출 전 착석·노쇼 불가 규칙은 상태 머신이 지킨다: CALLED → NO_SHOW
        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/no-show"), posA))
                .andExpect(jsonPath("$.item.status").value("NO_SHOW"));
        mvc.perform(pos(post("/api/v1/pos/waitings/" + id + "/seat"), posA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(device(get("/api/v1/journeys/" + journeyId), dev))
                .andExpect(jsonPath("$.status").value("ENDED"));
    }

    @Test
    @DisplayName("2-6 앞 팀이 호출되면 뒤 팀의 \"앞에 N팀\" 이 줄어든다 / 다른 매장 POS 는 404")
    void aheadDecreasesWhenFrontCalled() throws Exception {
        String d1 = api.newDevice();
        long w1 = api.register(d1, api.startJourney(d1, 2, STORE_A), STORE_A, 2).get("waitingId").asLong();
        String d2 = api.newDevice();
        long w2 = api.register(d2, api.startJourney(d2, 2, STORE_A), STORE_A, 2).get("waitingId").asLong();

        mvc.perform(device(get("/api/v1/waitings/" + w2), d2))
                .andExpect(jsonPath("$.aheadTeams").value(1))
                .andExpect(jsonPath("$.estimatedWaitMin").value(5));

        mvc.perform(pos(post("/api/v1/pos/waitings/" + w1 + "/call"), api.posToken(STORE_B)))
                .andExpect(status().isNotFound());
        mvc.perform(pos(post("/api/v1/pos/waitings/" + w1 + "/call"), posA)).andExpect(status().isOk());

        mvc.perform(device(get("/api/v1/waitings/" + w2), d2))
                .andExpect(jsonPath("$.aheadTeams").value(0))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));
        mvc.perform(device(get("/api/v1/waitings/" + w1), d1))
                .andExpect(jsonPath("$.status").value("CALLED"))
                .andExpect(jsonPath("$.aheadTeams").value(0));
    }
}
