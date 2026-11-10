package com.smartjourney.server.waiting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /waitings 의 Idempotency-Key 기록.
 * 음성 승인과 화면 승인이 거의 동시에 오거나, 터널에서 재시도해도 웨이팅은 한 번만 생긴다.
 * 같은 키 + 같은 요청 → 저장된 응답 그대로 / 같은 키 + 다른 요청 → 409 IDEMPOTENCY_CONFLICT
 */
@Getter
@Entity
@Table(name = "idempotency_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "idem_key", nullable = false, length = 64)
    private String idemKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body", nullable = false, length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IdempotencyRecord of(long deviceId, String idemKey, String requestHash,
                                       int statusCode, String responseBody, Instant now) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.deviceId = deviceId;
        r.idemKey = idemKey;
        r.requestHash = requestHash;
        r.statusCode = statusCode;
        r.responseBody = responseBody;
        r.createdAt = now;
        return r;
    }
}
