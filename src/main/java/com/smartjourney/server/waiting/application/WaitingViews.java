package com.smartjourney.server.waiting.application;

import com.smartjourney.server.arrival.domain.ArrivalCheck;
import com.smartjourney.server.arrival.domain.ArrivalCheckRepository;
import com.smartjourney.server.pos.api.PosDtos.ArrivalBadge;
import com.smartjourney.server.pos.api.PosDtos.PosQueueItem;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.waiting.api.WaitingDtos.WaitingResponse;
import com.smartjourney.server.waiting.domain.QueueCalculator;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 웨이팅 한 팀을 차량용·POS용 응답으로 바꾼다. (REST 응답과 STOMP payload 가 같은 모양이 되도록 한 곳에서)
 */
@Component
@RequiredArgsConstructor
public class WaitingViews {

    private final WaitingRepository waitingRepository;
    private final ArrivalCheckRepository arrivalCheckRepository;
    private final Clock clock;

    /** 내 앞에 줄 선 팀 수. 호출 이후·종료 상태는 0 */
    public int aheadOf(Waiting waiting) {
        if (!waiting.getStatus().isInLine()) {
            return 0;
        }
        return (int) waitingRepository.countByStoreIdAndStatusInAndQueueOrderLessThan(
                waiting.getStoreId(), WaitingStatus.COUNTED_AHEAD, waiting.getQueueOrder());
    }

    public int teamsInLine(long storeId) {
        return (int) waitingRepository.countByStoreIdAndStatusIn(storeId, WaitingStatus.COUNTED_AHEAD);
    }

    public WaitingResponse toResponse(Waiting waiting, Store store) {
        int ahead = aheadOf(waiting);
        int waitMin = waiting.getStatus().isInLine()
                ? QueueCalculator.estimatedWaitMinAt(store.getManualWaitMin(), ahead, teamsInLine(store.getId()),
                store.getMinutesPerTeam())
                : 0;
        return new WaitingResponse(
                waiting.getId(),
                store.getId(),
                store.getName(),
                waiting.getTicketNo(),
                waiting.getPartySize(),
                waiting.getStatus(),
                ahead,
                waitMin,
                waiting.getStatus().isInLine() ? QueueCalculator.estimatedEntryAt(clock.instant(), waitMin) : null,
                waiting.getDeferCount(),
                store.getMaxDefers(),
                waiting.getEtaMin());
    }

    public PosQueueItem toPosItem(Waiting waiting) {
        ArrivalBadge badge = arrivalCheckRepository.findFirstByWaitingIdOrderByCreatedAtDescIdDesc(waiting.getId())
                .map(this::badge)
                .orElse(null);
        return toPosItem(waiting, badge);
    }

    /** POS 보드: 여러 팀의 배지를 쿼리 한 번으로 */
    public List<PosQueueItem> toPosItems(List<Waiting> waitings) {
        Map<Long, ArrivalBadge> badges = latestBadges(waitings.stream().map(Waiting::getId).toList());
        return waitings.stream().map(w -> toPosItem(w, badges.get(w.getId()))).toList();
    }

    private Map<Long, ArrivalBadge> latestBadges(Collection<Long> waitingIds) {
        Map<Long, ArrivalBadge> result = new HashMap<>();
        if (waitingIds.isEmpty()) {
            return result;
        }
        // 오름차순이라 마지막에 넣은 값(가장 최근 보고)이 남는다
        for (ArrivalCheck check : arrivalCheckRepository.findByWaitingIdInOrderByCreatedAtAscIdAsc(waitingIds)) {
            result.put(check.getWaitingId(), badge(check));
        }
        return result;
    }

    private PosQueueItem toPosItem(Waiting waiting, ArrivalBadge badge) {
        return new PosQueueItem(
                waiting.getId(),
                waiting.getTicketNo(),
                waiting.getPartySize(),
                waiting.getChannel(),
                waiting.getChannel() == WaitingChannel.VEHICLE,
                waiting.getStatus(),
                waiting.getEtaMin(),
                waiting.getEtaUpdatedAt(),
                badge,
                waiting.getDeferCount(),
                waiting.getRegisteredAt());
    }

    private ArrivalBadge badge(ArrivalCheck check) {
        return new ArrivalBadge(check.getResult(), check.getAlighted(), check.getPartySize(), check.getArrivedAt());
    }
}
