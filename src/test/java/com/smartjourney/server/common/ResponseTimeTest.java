package com.smartjourney.server.common;

import static com.smartjourney.server.support.ApiFixture.STORE_A;
import static com.smartjourney.server.support.ApiFixture.STORE_B;
import static com.smartjourney.server.support.ApiFixture.device;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.support.ApiFixture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 3-4 음성 경로 응답 속도 가드: Gleo 콜백 7초 제한 → 접수·현황 p95 1초 미만.
 * (서버 안쪽 처리 시간만 잰다. 실제 네트워크 포함 측정은 scripts/load-check.sh)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResponseTimeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    @DisplayName("접수(POST /waitings)·현황(GET /journeys/current) p95 < 1초")
    void p95UnderOneSecond() throws Exception {
        ApiFixture api = new ApiFixture(mvc, om);
        api.resetStore(api.posToken(STORE_A));
        List<Long> register = new ArrayList<>();
        List<Long> status = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String dev = api.newDevice();
            long journeyId = api.startJourney(dev, 2, STORE_A, STORE_B);

            long t0 = System.nanoTime();
            api.register(dev, journeyId, STORE_A, 2);
            register.add(System.nanoTime() - t0);

            long t1 = System.nanoTime();
            mvc.perform(device(get("/api/v1/journeys/current"), dev)).andExpect(status().isOk());
            status.add(System.nanoTime() - t1);
        }
        assertThat(p95Millis(register)).as("접수 p95(ms)").isLessThan(1000);
        assertThat(p95Millis(status)).as("현황 p95(ms)").isLessThan(1000);
        System.out.printf("[3-4] register p95=%dms, current p95=%dms%n", p95Millis(register), p95Millis(status));
    }

    private static long p95Millis(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000;
    }
}
