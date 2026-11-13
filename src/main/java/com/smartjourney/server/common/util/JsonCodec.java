package com.smartjourney.server.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이벤트 payload·멱등 응답 저장용 JSON 변환 (Spring 의 ObjectMapper 설정 그대로).
 */
@Component
@RequiredArgsConstructor
public class JsonCodec {

    private final ObjectMapper objectMapper;

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 변환 실패", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 읽기 실패", e);
        }
    }
}
