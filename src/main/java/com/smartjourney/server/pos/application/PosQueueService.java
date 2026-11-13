package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.journey.application.JourneyProgress;
import com.smartjourney.server.pos.api.PosDtos.PosQueueResponse;
import com.smartjourney.server.pos.api.PosDtos.PosWaitingDetail;
import com.smartjourney.server.pos.api.PosDtos.PosWaitingEvent;
import com.smartjourney.server.pos.api.PosDtos.VerifyTicketResponse;
import com.smartjourney.server.store.api.StoreDtos.QueueStatusResponse;
import com.smartjourney.server.store.application.StoreQueryService;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.ticket.domain.QrTicketCodec;
import com.smartjourney.server.waiting.api.WaitingDtos.TicketResponse;
import com.smartjourney.server.waiting.application.QueueBroadcaster;
import com.smartjourney.server.waiting.application.WaitingHistory;
import com.smartjourney.server.waiting.application.WaitingService;
import com.smartjourney.server.waiting.application.WaitingViews;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingEventRepository;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POS 대기열 보드·호출·착석·노쇼 (P-02 ~ P-04, 기능 6.1).
 *
 * <ul>
 *   <li>queue: 살아 있는 팀(REGISTERED·ARRIVED·CALLED) queue_order 순 + 최신 하차 배지</li>
 *   <li>call: REGISTERED/ARRIVED → CALLED. 차량에 CALLED {qrPayload} (TTL 30분). 뒤 팀들 "앞에 N팀" 감소</li>
 *   <li>seat / noShow: CALLED → SEATED / NO_SHOW → 정차 DONE → WAITING_CLOSED (보드에서 빠짐)</li>
 *   <li>모든 명령은 토큰의 매장 웨이팅만 (다른 매장 웨이팅은 404 로 숨김)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PosQueueService {

    private final WaitingRepository waitingRepository;
    private final WaitingEventRepository waitingEventRepository;
    private final StoreRepository storeRepository;
    private final StoreQueryService storeQueryService;
    private final WaitingViews views;
    private final WaitingHistory history;
    private final QueueBroadcaster broadcaster;
    private final WaitingService waitingService;
    private final JourneyProgress journeyProgress;
    private final QrTicketCodec qrTicketCodec;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PosQueueResponse queue(long storeId) {
        Store store = storeQueryService.getStore(storeId);
        QueueStatusResponse status = storeQueryService.queueStatus(store);
        List<Waiting> active = waitingRepository.findByStoreIdAndStatusInOrderByQueueOrderAsc(
                storeId, WaitingStatus.ACTIVE);
        return new PosQueueResponse(storeId, status.aheadTeams(), status.estimatedWaitMin(), views.toPosItems(active));
    }

    @Transactional(readOnly = true)
    public PosWaitingDetail detail(long storeId, long waitingId) {
        return detailOf(inStore(storeId, waitingId));
    }

    @Transactional
    public PosWaitingDetail call(long storeId, long waitingId) {
        Store store = lockStore(storeId);
        Waiting waiting = inStore(storeId, waitingId);
        Instant now = clock.instant();
        transition(waiting, WaitingStatus.CALLED, now);
        history.record(waiting, WaitingEventType.CALLED);

        TicketResponse ticket = waitingService.issueTicket(waiting, store, now);
        broadcaster.called(waiting, ticket);
        broadcaster.queueChanged(store);     // 호출된 팀은 "앞 팀" 에서 빠진다 → 뒤 팀 숫자 감소
        return detailOf(waiting);
    }

    @Transactional
    public PosWaitingDetail seat(long storeId, long waitingId) {
        return close(storeId, waitingId, WaitingStatus.SEATED, WaitingEventType.SEATED);
    }

    @Transactional
    public PosWaitingDetail noShow(long storeId, long waitingId) {
        return close(storeId, waitingId, WaitingStatus.NO_SHOW, WaitingEventType.NO_SHOW);
    }

    private PosWaitingDetail close(long storeId, long waitingId, WaitingStatus next, WaitingEventType type) {
        Store store = lockStore(storeId);
        Waiting waiting = inStore(storeId, waitingId);
        transition(waiting, next, clock.instant());
        history.record(waiting, type);
        journeyProgress.waitingClosed(waiting);
        broadcaster.toPos(RealtimeEventType.WAITING_CLOSED, waiting);
        broadcaster.waitingUpdated(waiting, store);
        broadcaster.queueChanged(store);
        return detailOf(waiting);
    }

    /**
     * 입장 QR 확인 (P2). 서명·만료·매장·상태(CALLED)를 본다. 확인만 하고 착석 처리는 따로 누른다.
     */
    @Transactional(readOnly = true)
    public VerifyTicketResponse verifyTicket(long storeId, String qrPayload) {
        return qrTicketCodec.decode(qrPayload, clock.instant())
                .filter(t -> t.storeId() == storeId)
                .flatMap(t -> waitingRepository.findByIdAndStoreId(t.waitingId(), storeId)
                        .filter(w -> w.getTicketNo() == t.ticketNo())
                        .filter(w -> w.getStatus() == WaitingStatus.CALLED))
                .map(w -> new VerifyTicketResponse(true, w.getId(), w.getTicketNo(), w.getPartySize()))
                .orElseGet(() -> new VerifyTicketResponse(false, null, null, null));
    }

    // ------------------------------------------------------------------

    private PosWaitingDetail detailOf(Waiting waiting) {
        List<PosWaitingEvent> events = waitingEventRepository.findByWaitingIdOrderByOccurredAtAscIdAsc(waiting.getId())
                .stream()
                .map(e -> new PosWaitingEvent(e.getType(), e.getPayload(), e.getOccurredAt()))
                .toList();
        return new PosWaitingDetail(views.toPosItem(waiting), events);
    }

    private Waiting inStore(long storeId, long waitingId) {
        return waitingRepository.findByIdAndStoreId(waitingId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
    }

    private Store lockStore(long storeId) {
        return storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void transition(Waiting waiting, WaitingStatus next, Instant now) {
        try {
            waiting.transitionTo(next, now);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "지금 상태(" + waiting.getStatus() + ")에서는 할 수 없습니다.");
        }
    }
}
