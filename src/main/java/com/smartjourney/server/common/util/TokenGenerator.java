package com.smartjourney.server.common.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 기기 토큰처럼 추측 불가능해야 하는 값을 만든다. (VIN·기기 고유값은 절대 쓰지 않는다 — 가명 처리)
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    /** URL-safe Base64, 32바이트 → 43자 */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
