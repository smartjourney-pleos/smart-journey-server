package com.smartjourney.server.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.common.security.AuthHeaders;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 통합 테스트용 짧은 호출 모음. (데모 매장 1 = 42dot 5분/팀, 2 = 소프트웨어드림센터 3분/팀, PIN 1234)
 */
public final class ApiFixture {

    public static final long STORE_A = 1L;
    public static final long STORE_B = 2L;

    private final MockMvc mvc;
    private final ObjectMapper om;

    public ApiFixture(MockMvc mvc, ObjectMapper om) {
        this.mvc = mvc;
        this.om = om;
    }

    public String newDevice() throws Exception {
        return read(mvc.perform(json(post("/api/v1/devices"), "{\"consentVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isCreated())).get("deviceToken").asText();
    }

    public String posToken(long storeId) throws Exception {
        return read(mvc.perform(json(post("/api/v1/pos/login"), "{\"storeId\":" + storeId + ",\"pin\":\"1234\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    public void resetStore(String posToken) throws Exception {
        mvc.perform(post("/api/v1/pos/demo/reset").header("Authorization", "Bearer " + posToken))
                .andExpect(status().isOk());
    }

    public long startJourney(String device, int partySize, long... storeIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long id : storeIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        return read(mvc.perform(device(json(post("/api/v1/journeys"),
                        "{\"partySize\":" + partySize + ",\"partySource\":\"SENSOR\",\"storeIds\":[" + ids + "]}"), device))
                .andExpect(status().isCreated())).get("journeyId").asLong();
    }

    public JsonNode register(String device, long journeyId, long storeId, int partySize) throws Exception {
        return read(registerRaw(device, UUID.randomUUID().toString(), journeyId, storeId, partySize, "TOUCH")
                .andExpect(status().isCreated()));
    }

    public ResultActions registerRaw(String device, String key, long journeyId, long storeId, int partySize,
                                     String approval) throws Exception {
        MockHttpServletRequestBuilder req = device(json(post("/api/v1/waitings"),
                "{\"journeyId\":" + journeyId + ",\"storeId\":" + storeId + ",\"partySize\":" + partySize
                        + ",\"approvalMethod\":\"" + approval + "\"}"), device);
        if (key != null) {
            req.header(AuthHeaders.IDEMPOTENCY_KEY, key);
        }
        return mvc.perform(req);
    }

    public static MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder req, String token) {
        return req.header(AuthHeaders.DEVICE_TOKEN, token);
    }

    public static MockHttpServletRequestBuilder pos(MockHttpServletRequestBuilder req, String jwt) {
        return req.header("Authorization", "Bearer " + jwt);
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public JsonNode read(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
