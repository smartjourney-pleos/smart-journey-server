package com.smartjourney.server.common.security;

import com.smartjourney.server.common.realtime.Topics;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * STOMP SUBSCRIBE 권한 검사.
 * <ul>
 *   <li>/topic/stores/{id}/queue : 인증된 차량·POS 누구나 (대기 팀 수·예상 대기만 담기므로 개인정보 없음)</li>
 *   <li>/topic/waitings/{id}    : 그 웨이팅을 접수한 기기만</li>
 *   <li>/topic/pos/{storeId}    : 그 매장 POS 만</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SubscriptionAuthorizer {

    private final WaitingRepository waitingRepository;

    public boolean canSubscribe(Object principal, String destination) {
        if (destination == null || principal == null) {
            return false;
        }
        if (destination.startsWith(Topics.STORE_QUEUE_PREFIX) && destination.endsWith("/queue")) {
            return true;
        }
        if (destination.startsWith(Topics.WAITING_PREFIX)) {
            Optional<Long> waitingId = parseId(destination.substring(Topics.WAITING_PREFIX.length()));
            return principal instanceof DevicePrincipal device
                    && waitingId.isPresent()
                    && waitingRepository.existsByIdAndDeviceId(waitingId.get(), device.deviceId());
        }
        if (destination.startsWith(Topics.POS_PREFIX)) {
            Optional<Long> storeId = parseId(destination.substring(Topics.POS_PREFIX.length()));
            return principal instanceof PosPrincipal pos
                    && storeId.isPresent()
                    && storeId.get() == pos.storeId();
        }
        return false;
    }

    private Optional<Long> parseId(String raw) {
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
