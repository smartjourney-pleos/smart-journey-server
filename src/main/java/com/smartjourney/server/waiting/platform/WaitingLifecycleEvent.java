package com.smartjourney.server.waiting.platform;

import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingStatus;

/**
 * 웨이팅에 일이 생겼다는 내부 이벤트 (기기 토큰·좌표 없음). 외부 연동은 이것만 보고 움직인다.
 */
public record WaitingLifecycleEvent(
        long waitingId,
        long storeId,
        WaitingChannel channel,
        WaitingEventType type,
        WaitingStatus status,
        int partySize,
        int ticketNo
) {
}
