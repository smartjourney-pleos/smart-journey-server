package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.config.ClockConfig;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.journey.application.JourneyProgress;
import com.smartjourney.server.pos.api.PosDtos.DemoResult;
import com.smartjourney.server.store.api.StoreDtos.QueueStatusResponse;
import com.smartjourney.server.store.application.StoreQueryService;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.waiting.application.QueueBroadcaster;
import com.smartjourney.server.waiting.application.WaitingHistory;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 패널 (P-06). 2차 PT 핵심 장면: 심사위원이 현장 대기 팀을 늘리면 차량 상태 알림 숫자가 바로 바뀐다 (T2).
 *
 * <ul>
 *   <li>addWalkIns: 매장 잠금 → Waiting.walkIn × count → QUEUE_UPDATED 는 마지막에 1번만</li>
 *   <li>removeWalkIns: 가장 늦게 들어온 줄 선 WALK_IN 부터 CANCELLED</li>
 *   <li>reset: 살아 있는 팀 전부 정리 (리허설 반복용). 줄 선 팀은 CANCELLED, 호출된 팀은 NO_SHOW</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PosDemoService {

    private final AppProperties properties;
    private final StoreRepository storeRepository;
    private final StoreQueryService storeQueryService;
    private final WaitingRepository waitingRepository;
    private final WaitingHistory history;
    private final QueueBroadcaster broadcaster;
    private final JourneyProgress journeyProgress;
    private final Clock clock;

    @Transactional
    public DemoResult addWalkIns(long storeId, int count, int partySize) {
        requireDemoEnabled();
        Store store = lockStore(storeId);
        Instant now = clock.instant();
        LocalDate businessDate = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);
        int ticketNo = waitingRepository.findMaxTicketNo(storeId, businessDate);
        for (int i = 0; i < count; i++) {
            // 같은 ms 에 여러 팀이 들어가도 순번이 겹치지 않게 1ms 씩
            Waiting w = waitingRepository.save(Waiting.walkIn(storeId, businessDate, ++ticketNo, partySize,
                    now.plusMillis(i)));
            history.record(w, WaitingEventType.REGISTERED, Map.of("partySize", partySize, "by", "DEMO"));
            broadcaster.toPos(RealtimeEventType.WAITING_CREATED, w);
        }
        broadcaster.queueChanged(store);
        return result(store, count);
    }

    @Transactional
    public DemoResult removeWalkIns(long storeId, int count) {
        requireDemoEnabled();
        Store store = lockStore(storeId);
        List<Waiting> latest = waitingRepository.findByStoreIdAndChannelAndStatusInOrderByQueueOrderDesc(
                storeId, WaitingChannel.WALK_IN, WaitingStatus.COUNTED_AHEAD);
        int removed = 0;
        for (Waiting w : latest.subList(0, Math.min(count, latest.size()))) {
            w.transitionTo(WaitingStatus.CANCELLED, clock.instant());
            history.record(w, WaitingEventType.CANCELLED, Map.of("by", "DEMO"));
            broadcaster.toPos(RealtimeEventType.WAITING_CANCELLED, w);
            removed++;
        }
        if (removed > 0) {
            broadcaster.queueChanged(store);
        }
        return result(store, removed);
    }

    @Transactional
    public DemoResult reset(long storeId) {
        requireDemoEnabled();
        Store store = lockStore(storeId);
        int changed = 0;
        for (Waiting w : waitingRepository.findByStoreIdAndStatusInOrderByQueueOrderAsc(storeId, WaitingStatus.ACTIVE)) {
            boolean called = w.getStatus() == WaitingStatus.CALLED;
            w.transitionTo(called ? WaitingStatus.NO_SHOW : WaitingStatus.CANCELLED, clock.instant());
            history.record(w, called ? WaitingEventType.NO_SHOW : WaitingEventType.CANCELLED, Map.of("by", "DEMO_RESET"));
            journeyProgress.waitingClosed(w);
            broadcaster.toPos(called ? RealtimeEventType.WAITING_CLOSED : RealtimeEventType.WAITING_CANCELLED, w);
            broadcaster.waitingUpdated(w, store);
            changed++;
        }
        broadcaster.queueChanged(store);
        return result(store, changed);
    }

    private DemoResult result(Store store, int changed) {
        QueueStatusResponse status = storeQueryService.queueStatus(store);
        return new DemoResult(changed, status.aheadTeams(), status.estimatedWaitMin());
    }

    private Store lockStore(long storeId) {
        return storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void requireDemoEnabled() {
        if (!properties.demo().enabled()) {
            throw new BusinessException(ErrorCode.DEMO_DISABLED);
        }
    }
}
