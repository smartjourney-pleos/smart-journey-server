package com.smartjourney.server.common.security;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 0 뼈대 검증: 동의 → 토큰 → 차량 API / 철회 → 403 / POS PIN 로그인 → JWT.
 * (application-test.yml: H2 메모리 + 데모 매장 2곳 시드)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("고지 문구는 인증 없이 볼 수 있다")
    void noticeIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/consent/notice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2026-11-v1"))
                .andExpect(jsonPath("$.retentionDays").value(30));
    }

    @Test
    @DisplayName("토큰 없이 차량 API → 401 {code}")
    void deviceApiRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/stores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("동의하면 토큰 발급 → 매장 목록 → 철회하면 403 CONSENT_REQUIRED")
    void consentLifecycle() throws Exception {
        String token = registerDevice();

        mockMvc.perform(get("/api/v1/stores").header(AuthHeaders.DEVICE_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].queue.estimatedWaitMin").value(0));

        mockMvc.perform(delete("/api/v1/devices/me/consent").header(AuthHeaders.DEVICE_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentActive").value(false));

        mockMvc.perform(get("/api/v1/stores").header(AuthHeaders.DEVICE_TOKEN, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        // 철회 상태에서도 내 상태 조회는 된다
        mockMvc.perform(get("/api/v1/devices/me").header(AuthHeaders.DEVICE_TOKEN, token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("옛 고지 버전으로는 동의할 수 없다")
    void rejectsStaleConsentVersion() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentVersion\":\"2020-01-v0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POS: PIN 1234 로그인 → 설정 조회 / 틀린 PIN → 401")
    void posLogin() throws Exception {
        mockMvc.perform(post("/api/v1/pos/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":1,\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("POS_LOGIN_FAILED"));

        String body = mockMvc.perform(post("/api/v1/pos/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":1,\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = objectMapper.readTree(body).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/pos/settings").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesPerTeam").value(5))
                .andExpect(jsonPath("$.arrivalBufferMin").value(10));

        // 차량 토큰으로는 POS API 불가
        mockMvc.perform(get("/api/v1/pos/settings").header(AuthHeaders.DEVICE_TOKEN, registerDevice()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("아직 구현 안 된 API 는 501 NOT_IMPLEMENTED 로 Phase 를 알려 준다")
    void notImplementedYet() throws Exception {
        mockMvc.perform(get("/api/v1/journeys/current").header(AuthHeaders.DEVICE_TOKEN, registerDevice()))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    private String registerDevice() throws Exception {
        String body = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("deviceToken").asText();
    }
}
