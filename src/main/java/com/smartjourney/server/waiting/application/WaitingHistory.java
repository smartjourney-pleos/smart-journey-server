package com.smartjourney.server.waiting.application;

import com.smartjourney.server.common.util.JsonCodec;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingEvent;
import com.smartjourney.server.waiting.domain.WaitingEventRepository;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.platform.WaitingLifecycleEvent;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 웨이팅 감사 로그 (POS 팀 상세 P-03 · KPI). 좌표·토큰 같은 값은 payload 에 넣지 않는다.
 * 기록과 함께 {@link WaitingLifecycleEvent} 를 발행해 외부 웨이팅 플랫폼 어댑터가 커밋 후 따라갈 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingHistory {

    private final WaitingEventRepository repository;
    private final JsonCodec json;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public void record(Waiting waiting, WaitingEventType type, Map<String, ?> payload) {
        // 키 순서를 고정해 POS 이벤트 기록이 매번 같은 모양으로 보이게
        repository.save(WaitingEvent.of(waiting.getId(), type, payload == null || payload.isEmpty() ? null
                : json.write(new TreeMap<>(payload)), clock.instant()));
        events.publishEvent(new WaitingLifecycleEvent(waiting.getId(), waiting.getStoreId(), waiting.getChannel(),
                type, waiting.getStatus(), waiting.getPartySize(), waiting.getTicketNo()));
    }

    public void record(Waiting waiting, WaitingEventType type) {
        record(waiting, type, null);
    }
}
