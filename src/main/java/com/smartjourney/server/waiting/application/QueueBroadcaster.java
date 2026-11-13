package com.smartjourney.server.waiting.application;

import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.common.realtime.RealtimeMessage;
import com.smartjourney.server.common.realtime.Topics;
import com.smartjourney.server.store.application.StoreQueryService;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.waiting.api.WaitingDtos.TicketResponse;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * STOMP 이벤트 발행 한 곳. 모두 {@link RealtimeMessage} 로 발행 → RealtimeRelay 가 커밋 후 전송.
 *
 * <ul>
 *   <li>/topic/stores/{id}/queue : QUEUE_UPDATED (새로 줄 서는 팀 기준 대기 현황)</li>
 *   <li>/topic/waitings/{id}    : WAITING_UPDATED (= GET /waitings/{id} 응답), CALLED (= GET /ticket 응답)</li>
 *   <li>/topic/pos/{storeId}    : WAITING_CREATED 등 — payload 는 항상 POS 보드 카드 한 장 (PosQueueItem)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class QueueBroadcaster {

    private final ApplicationEventPublisher events;
    private final StoreQueryService storeQueryService;
    private final WaitingRepository waitingRepository;
    private final WaitingViews views;

    /**
     * 매장 대기열이 바뀌었다 → 매장 대기 현황 1번 + 그 매장에 줄 선 차량들 각자의 순번 (2-6 "앞에 N팀" 갱신).
     * 여러 팀을 한꺼번에 바꿔도 한 번만 부른다 (데모 패널 +N → QUEUE_UPDATED 1회).
     */
    public void queueChanged(Store store) {
        events.publishEvent(new RealtimeMessage(
                Topics.storeQueue(store.getId()), RealtimeEventType.QUEUE_UPDATED, storeQueryService.queueStatus(store)));
        for (Waiting w : waitingRepository.findByStoreIdAndStatusInOrderByQueueOrderAsc(
                store.getId(), WaitingStatus.ACTIVE)) {
            if (w.getChannel() == WaitingChannel.VEHICLE) {
                waitingUpdated(w, store);
            }
        }
    }

    /** 그 웨이팅을 접수한 차량에게 현재 상태 */
    public void waitingUpdated(Waiting waiting, Store store) {
        if (waiting.getChannel() != WaitingChannel.VEHICLE) {
            return;
        }
        events.publishEvent(new RealtimeMessage(
                Topics.waiting(waiting.getId()), RealtimeEventType.WAITING_UPDATED, views.toResponse(waiting, store)));
    }

    public void called(Waiting waiting, TicketResponse ticket) {
        if (waiting.getChannel() != WaitingChannel.VEHICLE) {
            return;
        }
        events.publishEvent(new RealtimeMessage(Topics.waiting(waiting.getId()), RealtimeEventType.CALLED, ticket));
    }

    public void toPos(RealtimeEventType type, Waiting waiting) {
        events.publishEvent(new RealtimeMessage(Topics.pos(waiting.getStoreId()), type, views.toPosItem(waiting)));
    }
}
