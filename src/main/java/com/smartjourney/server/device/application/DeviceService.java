package com.smartjourney.server.device.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.util.TokenGenerator;
import com.smartjourney.server.device.api.DeviceDtos.DeviceStatusResponse;
import com.smartjourney.server.device.api.DeviceDtos.RegisterDeviceResponse;
import com.smartjourney.server.device.domain.ConsentNotice;
import com.smartjourney.server.device.domain.Device;
import com.smartjourney.server.device.domain.DeviceRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
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
     * TODO(Phase 3): 진행 중인 웨이팅이 있으면 취소 처리 + 보유 데이터 즉시 파기 여부 결정
     */
    @Transactional
    public DeviceStatusResponse revoke(long deviceId) {
        Device device = get(deviceId);
        device.revoke(clock.instant());
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
