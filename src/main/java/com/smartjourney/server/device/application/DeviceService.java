package com.smartjourney.server.device.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.util.TokenGenerator;
import com.smartjourney.server.device.api.DeviceDtos.DeviceStatusResponse;
import com.smartjourney.server.device.api.DeviceDtos.RegisterDeviceResponse;
import com.smartjourney.server.device.domain.ConsentNotice;
import com.smartjourney.server.device.domain.Device;
import com.smartjourney.server.device.domain.DeviceConsentRevoked;
import com.smartjourney.server.device.domain.DeviceRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 6.3 외부 전송 고지 및 동의 (P0-6).
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AppProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ConsentNotice currentNotice() {
        return ConsentNotice.current(properties.consent().currentVersion(), properties.consent().retentionDays());
    }

    @Transactional
    public RegisterDeviceResponse register(String consentVersion) {
        requireCurrentVersion(consentVersion);
        Device device = deviceRepository.save(
                Device.consented(TokenGenerator.newToken(), consentVersion, clock.instant()));
        return new RegisterDeviceResponse(device.getDeviceToken(), device.getConsentVersion(), device.getConsentedAt());
    }

    @Transactional(readOnly = true)
    public DeviceStatusResponse status(long deviceId) {
        Device device = get(deviceId);
        return toStatus(device);
    }

    @Transactional
    public DeviceStatusResponse reconsent(long deviceId, String consentVersion) {
        requireCurrentVersion(consentVersion);
        Device device = get(deviceId);
        device.reconsent(consentVersion, clock.instant());
        return toStatus(device);
    }

    /**
     * 철회. 이후 전송은 필터에서 전부 막힌다.
     * 정책 (3-6): 진행 중 여정은 끝내고 줄 서 있는 웨이팅은 취소해 매장에 알린다 (같은 트랜잭션).
     * 이미 호출된 팀은 매장에 있으므로 그대로 두고, 쌓인 기록은 보유 기간(30일) 뒤 DataRetentionJob 이 파기한다.
     */
    @Transactional
    public DeviceStatusResponse revoke(long deviceId) {
        Device device = get(deviceId);
        boolean wasActive = !device.isRevoked();
        device.revoke(clock.instant());
        if (wasActive) {
            events.publishEvent(new DeviceConsentRevoked(deviceId));
        }
        return toStatus(device);
    }

    private void requireCurrentVersion(String consentVersion) {
        if (!properties.consent().currentVersion().equals(consentVersion)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "고지 문구 버전이 최신이 아닙니다. GET /api/v1/consent/notice 로 다시 받아 주세요.");
        }
    }

    private Device get(long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private DeviceStatusResponse toStatus(Device device) {
        return new DeviceStatusResponse(
                device.getConsentVersion(),
                device.getConsentedAt(),
                device.getRevokedAt(),
                !device.isRevoked(),
                !properties.consent().currentVersion().equals(device.getConsentVersion()));
    }
}
