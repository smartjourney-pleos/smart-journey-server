package com.smartjourney.server.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 배포(postgres 프로필)에서 개발용 비밀값으로 뜨는 것을 막는다. .env 를 안 채우고 올리면 부팅 실패 → 바로 알아챈다.
 */
@Component
@Profile("postgres")
@RequiredArgsConstructor
public class ProductionSecretsGuard {

    private static final String DEV_MARKER = "local-dev-only";
    private static final int MIN_LENGTH = 32;

    private final AppProperties properties;

    @PostConstruct
    void check() {
        require("JWT_SECRET", properties.auth().jwtSecret());
        require("QR_SECRET", properties.qr().secret());
    }

    private static void require(String name, String value) {
        if (value == null || value.length() < MIN_LENGTH || value.contains(DEV_MARKER)) {
            throw new IllegalStateException(name + " 를 .env 에 32자 이상 임의 값으로 넣어 주세요 (openssl rand -base64 48)");
        }
    }
}
