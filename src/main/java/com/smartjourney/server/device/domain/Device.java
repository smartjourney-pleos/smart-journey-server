package com.smartjourney.server.device.domain;

import com.smartjourney.server.common.entity.BaseTimeEntity;
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
 * 차량(기기). 외부 전송 동의 시 생성되고, 임의 토큰으로만 식별한다.
 * VIN·차량 모델·좌표 같은 값은 저장하지 않는다 (정책 4, Pleos 보안·개인정보 설계 가이드 "차량 ID 가명화").
 */
@Getter
@Entity
@Table(name = "device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_token", nullable = false, unique = true, length = 64)
    private String deviceToken;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public static Device consented(String deviceToken, String consentVersion, Instant now) {
        Device device = new Device();
        device.deviceToken = deviceToken;
        device.consentVersion = consentVersion;
        device.consentedAt = now;
        device.lastSeenAt = now;
        return device;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 철회: 이후 외부 전송 API 는 전부 403 CONSENT_REQUIRED */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** 재동의 또는 고지 문구 버전 갱신 */
    public void reconsent(String consentVersion, Instant now) {
        this.consentVersion = consentVersion;
        this.consentedAt = now;
        this.revokedAt = null;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }
}
