package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.common.realtime.RealtimeMessage;
import com.smartjourney.server.common.realtime.Topics;
import com.smartjourney.server.pos.api.PosDtos.PosSettingsResponse;
import com.smartjourney.server.pos.api.PosDtos.UpdatePosSettingsRequest;
import com.smartjourney.server.store.application.StoreQueryService;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 설정 (P-05). 팀당 분·도착 여유·수동 대기시간이 바뀌면 차량 골든타임이 바로 다시 계산되도록 QUEUE_UPDATED 발행.
 */
@Service
@RequiredArgsConstructor
public class PosSettingsService {

    private final StoreRepository storeRepository;
    private final StoreQueryService storeQueryService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PosSettingsResponse get(long storeId) {
        return toResponse(load(storeId));
    }

    @Transactional
    public PosSettingsResponse update(long storeId, UpdatePosSettingsRequest request) {
        Store store = load(storeId);
        store.updateSettings(request.minutesPerTeam(), request.arrivalBufferMin(), request.manualWaitMin(),
                request.clearManualWait());
        // 커밋 후 전송 (RealtimeRelay)
        events.publishEvent(new RealtimeMessage(
                Topics.storeQueue(storeId), RealtimeEventType.QUEUE_UPDATED, storeQueryService.queueStatus(store)));
        return toResponse(store);
    }

    private Store load(long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private PosSettingsResponse toResponse(Store store) {
        return new PosSettingsResponse(store.getMinutesPerTeam(), store.getArrivalBufferMin(),
                store.getManualWaitMin(), store.getMaxDefers());
    }
}
