package com.smartjourney.server.kpi;

import static com.smartjourney.server.support.ApiFixture.STORE_B;
import static com.smartjourney.server.support.ApiFixture.device;
import static com.smartjourney.server.support.ApiFixture.json;
import static com.smartjourney.server.support.ApiFixture.pos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.privacy.application.DataRetentionJob;
import com.smartjourney.server.support.ApiFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 3-5 KPI 이벤트·일 집계 · 4-8 보유 기간 파기 (파기 전 KPI 스냅샷).
 */
@SpringBootTest
@AutoConfigureMockMvc
class KpiAndRetentionTest {

    private static final String OPS = "test-ops-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataRetentionJob retentionJob;

    ApiFixture api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiFixture(mvc, om);
        api.resetStore(api.posToken(STORE_B));
    }

    @Test
    @DisplayName("3-5 KPI: 차량 이벤트 보고 → 오늘 집계에 반영 / 좌표 키 400 / 토큰 없으면 401")
    void kpiDaily() throws Exception {
        JsonNode before = today();

        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_B);
        for (String type : new String[] {"PROMPT_SHOWN", "PROMPT_SHOWN", "PROMPT_DECLINED", "VOICE_COMMAND"}) {
            mvc.perform(device(json(post("/api/v1/journeys/" + journeyId + "/events"),
                            "{\"type\":\"" + type + "\",\"storeId\":2,\"attributes\":{\"cardId\":\"c1\"}}"), dev))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(device(json(post("/api/v1/journeys/" + journeyId + "/events"),
                        "{\"type\":\"PROMPT_SHOWN\",\"attributes\":{\"latitude\":37.3}}"), dev))
                .andExpect(status().isBadRequest());

        api.read(api.registerRaw(dev, UUID.randomUUID().toString(), journeyId, STORE_B, 2, "VOICE")
                .andExpect(status().isCreated()));

        JsonNode after = today();
        assertThat(after.get("snapshot").asBoolean()).isFalse();
        assertThat(delta(before, after, "journeys", "started")).isEqualTo(1);
        assertThat(delta(before, after, "journeys", "sensorAccepted")).isEqualTo(1);
        assertThat(delta(before, after, "prompts", "shown")).isEqualTo(2);
        assertThat(delta(before, after, "prompts", "declined")).isEqualTo(1);
        assertThat(delta(before, after, "prompts", "registered")).isEqualTo(1);
        assertThat(delta(before, after, "approvals", "voice")).isEqualTo(1);
        assertThat(delta(before, after, "driving", "voiceCommands")).isEqualTo(1);
        assertThat(after.get("approvals").get("voiceRate").isNumber()).isTrue();

        mvc.perform(get("/api/v1/ops/kpi/daily")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ops/kpi/daily").header("X-Ops-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ops/kpi/daily?from=2026-01-01&to=2026-12-31").header("X-Ops-Token", OPS))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4-8 보유 기간(30일) 지난 웨이팅·여정은 파기, 그날 KPI 는 스냅샷으로 남는다")
    void retentionPurgesAndSnapshots() throws Exception {
        String dev = api.newDevice();
        long journeyId = api.startJourney(dev, 2, STORE_B);
        long waitingId = api.register(dev, journeyId, STORE_B, 2).get("waitingId").asLong();
        String posB = api.posToken(STORE_B);
        mvc.perform(pos(post("/api/v1/pos/waitings/" + waitingId + "/call"), posB)).andExpect(status().isOk());
        mvc.perform(pos(post("/api/v1/pos/waitings/" + waitingId + "/seat"), posB)).andExpect(status().isOk());

        // 40일 전 일로 만든다
        Instant old = Instant.now().minus(40, ChronoUnit.DAYS);
        Timestamp ts = Timestamp.from(old);
        jdbc.update("update waiting set registered_at = ?, closed_at = ? where id = ?", ts, ts, waitingId);
        jdbc.update("update journey set started_at = ?, ended_at = ? where id = ?", ts, ts, journeyId);
        LocalDate oldDate = LocalDate.ofInstant(old, ZoneId.of("Asia/Seoul"));

        DataRetentionJob.PurgeResult result = retentionJob.purgeExpired();
        assertThat(result.waitings()).isGreaterThanOrEqualTo(1);
        assertThat(result.journeys()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from waiting where id = ?", Integer.class, waitingId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from waiting_event where waiting_id = ?", Integer.class,
                waitingId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from journey where id = ?", Integer.class, journeyId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from journey_stop where journey_id = ?", Integer.class,
                journeyId)).isZero();

        JsonNode day = api.read(mvc.perform(get("/api/v1/ops/kpi/daily?from=" + oldDate + "&to=" + oldDate)
                .header("X-Ops-Token", OPS)).andExpect(status().isOk())).get("days").get(0);
        assertThat(day.get("snapshot").asBoolean()).isTrue();
        assertThat(day.get("outcomes").get("seated").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(day.get("journeys").get("started").asInt()).isGreaterThanOrEqualTo(1);

        // 다시 돌려도 안전 (스냅샷 중복 없음)
        retentionJob.purgeExpired();
        mvc.perform(get("/api/v1/ops/kpi/daily?from=" + oldDate + "&to=" + oldDate).header("X-Ops-Token", OPS))
                .andExpect(jsonPath("$.days[0].snapshot").value(true));
    }

    private JsonNode today() throws Exception {
        return api.read(mvc.perform(get("/api/v1/ops/kpi/daily").header("X-Ops-Token", OPS))
                .andExpect(status().isOk())).get("days").get(6);
    }

    private static int delta(JsonNode before, JsonNode after, String group, String field) {
        return after.get(group).get(field).asInt() - before.get(group).get(field).asInt();
    }
}
