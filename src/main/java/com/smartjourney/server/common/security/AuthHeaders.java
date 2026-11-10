package com.smartjourney.server.common.security;

/**
 * 인증 관련 헤더 이름. 토큰은 항상 헤더로만 받는다 (URL 경로·쿼리 금지 — Pleos 앱 보안 가이드).
 */
public final class AuthHeaders {

    /** 차량 앱: 동의 시 발급받은 기기 토큰 */
    public static final String DEVICE_TOKEN = "X-Device-Token";

    /** POS: Authorization: Bearer &lt;JWT&gt; */
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /** 웨이팅 접수 중복 방지 (네트워크 재시도·음성/화면 동시 승인) */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private AuthHeaders() {
    }
}
