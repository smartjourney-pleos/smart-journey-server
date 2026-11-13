package com.smartjourney.server.store.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.store.api.StoreDtos.QueueStatusResponse;
import com.smartjourney.server.store.api.StoreDtos.StoreSummary;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.waiting.domain.QueueCalculator;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 조회 + 대기 현황(골든타임 입력값). 기능 2.1 · 6.1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final WaitingRepository waitingRepository;
    private final Clock clock;

    public List<StoreSummary> list() {
        return storeRepository.findAll().stream().map(this::toSummary).toList();
    }

    public StoreSummary get(long storeId) {
        return toSummary(getStore(storeId));
    }

    public QueueStatusResponse queueStatus(long storeId) {
        return queueStatus(getStore(storeId));
    }

    /** 새로 줄을 서는 팀 기준(맨 뒤) 대기 현황 */
    public QueueStatusResponse queueStatus(Store store) {
        int aheadTeams = (int) waitingRepository.countByStoreIdAndStatusIn(store.getId(), WaitingStatus.COUNTED_AHEAD);
        int waitMin = QueueCalculator.estimatedWaitMin(store.getManualWaitMin(), aheadTeams, store.getMinutesPerTeam());
        return new QueueStatusResponse(store.getId(), aheadTeams, waitMin, store.getArrivalBufferMin(),
                store.getManualWaitMin() != null, clock.instant());
    }

    public Store getStore(long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreSummary toSummary(Store store) {
        return new StoreSummary(
                store.getId(), store.getName(), store.getAddress(),
                store.getLatitude(), store.getLongitude(), store.getPoiId(), store.getPoiSubId(),
                store.isOpenAt(LocalTime.now(clock)), store.getAvgStayMin(),
                queueStatus(store));
    }
}
