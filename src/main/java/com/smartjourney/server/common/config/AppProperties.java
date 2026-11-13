package com.smartjourney.server.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 smartjourney.* 설정.
 */
@ConfigurationProperties(prefix = "smartjourney")
public record AppProperties(
        Consent consent,
        Auth auth,
        Qr qr,
        Web web,
        Demo demo,
        Route route,
        Ops ops
) {

    /** 외부 전송 동의 고지 버전과 보유 기간 (정책 4) */
    public record Consent(String currentVersion, int retentionDays) {
    }

    /** POS 로그인 JWT */
    public record Auth(String jwtSecret, Duration posTokenTtl) {
    }

    /** 호출 시 차량에 내려주는 번호표 QR 서명 */
    public record Qr(String secret, Duration ttl) {
    }

    /** CORS(POS 웹) + WebSocket 허용 Origin */
    public record Web(List<String> allowedOriginPatterns) {
    }

    /** POS 데모 패널 허용 여부 (심사위원 조작용) */
    public record Demo(boolean enabled) {
    }

    /** 코스 순서 최적화 제공자: none | kakao (P1) */
    public record Route(String provider, String kakaoRestApiKey) {
    }

    /** 운영 지표 API 토큰 (X-Ops-Token). 비어 있으면 KPI API 꺼짐 */
    public record Ops(String token) {
    }
}
