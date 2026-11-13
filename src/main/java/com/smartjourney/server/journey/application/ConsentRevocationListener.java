package com.smartjourney.server.journey.application;

import com.smartjourney.server.device.domain.DeviceConsentRevoked;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 동의 철회 → 진행 중 여정 종료 + 줄 서 있는 웨이팅 취소 (철회 트랜잭션 안에서).
 */
@Component
@RequiredArgsConstructor
public class ConsentRevocationListener {

    private final JourneyService journeyService;

    @EventListener
    public void onRevoked(DeviceConsentRevoked event) {
        journeyService.endAllForRevokedDevice(event.deviceId());
    }
}
