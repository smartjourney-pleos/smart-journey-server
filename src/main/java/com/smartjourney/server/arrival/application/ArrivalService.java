package com.smartjourney.server.arrival.application;

import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalReportRequest;
import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalResponse;
import com.smartjourney.server.arrival.domain.ArrivalCheck;
import com.smartjourney.server.arrival.domain.ArrivalCheckRepository;
import com.smartjourney.server.arrival.domain.ArrivalEvidence;
import com.smartjourney.server.arrival.domain.ArrivalPolicy;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.waiting.application.QueueBroadcaster;
import com.smartjourney.server.waiting.application.WaitingHistory;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 6.2 도착·하차 확인 (P0-5).
 *
 * <ul>
 *   <li>ArrivalPolicy 위반이면 400 ARRIVAL_INVALID (예: UNCONFIRMED 인데 alighted 를 보냄)</li>
 *   <li>REGISTERED → ARRIVED. 이미 ARRIVED·CALLED 면 상태는 그대로, 기록만 남긴다 (도착 전 호출 케이스)</li>
 *   <li>같은 웨이팅에 여러 번 오면 마지막 보고가 유효 (UNCONFIRMED 뒤 MANUAL 로 갱신 가능)</li>
 *   <li>/topic/pos/{storeId} ARRIVAL_REPORTED → POS 배지 초록/회색/노랑</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ArrivalService {

    private final WaitingRepository waitingRepository;
    private final StoreRepository storeRepository;
    private final ArrivalCheckRepository arrivalCheckRepository;
    private final WaitingHistory history;
    private final QueueBroadcaster broadcaster;
    private final Clock clock;

    @Transactional
    public ArrivalResponse report(long deviceId, long waitingId, ArrivalReportRequest request) {
        ArrivalPolicy.violation(request.result(), request.alighted(), request.partySize())
                .ifPresent(reason -> {
                    throw new BusinessException(ErrorCode.ARRIVAL_INVALID, "하차 확인 값이 올바르지 않습니다: " + reason);
                });

        Waiting waiting = waitingRepository.findByIdAndDeviceId(waitingId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
        if (waiting.getStatus().isClosed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 끝난 웨이팅입니다.");
        }

        Instant now = clock.instant();
        if (waiting.getStatus() == WaitingStatus.REGISTERED) {
            waiting.transitionTo(WaitingStatus.ARRIVED, now);
        }
        ArrivalEvidence evidence = request.evidence() != null ? request.evidence() : ArrivalEvidence.empty();
        ArrivalCheck check = arrivalCheckRepository.save(ArrivalCheck.of(
                waiting.getId(), request.result(), request.alighted(), request.partySize(), evidence, now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", request.result());
        if (request.alighted() != null) {
            payload.put("alighted", request.alighted());
        }
        payload.put("partySize", request.partySize());
        history.record(waiting, WaitingEventType.ARRIVAL_REPORTED, payload);

        Store store = storeRepository.findById(waiting.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        broadcaster.toPos(RealtimeEventType.ARRIVAL_REPORTED, waiting);
        broadcaster.waitingUpdated(waiting, store);

        return new ArrivalResponse(waiting.getId(), check.getResult(), check.getAlighted(), check.getPartySize(),
                waiting.getStatus(), now);
    }
}
