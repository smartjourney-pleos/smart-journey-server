package com.smartjourney.server.arrival.application;

import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalReportRequest;
import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalResponse;
import com.smartjourney.server.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기능 6.2 도착·하차 확인 (P0-5).
 *
 * <p>구현 메모 (Phase 2):
 * <ul>
 *   <li>ArrivalPolicy.violation 이면 400 ARRIVAL_INVALID</li>
 *   <li>REGISTERED → ARRIVED 전이. 이미 CALLED 면 상태는 그대로 두고 ArrivalCheck 만 남긴다 (도착 전 호출 케이스)</li>
 *   <li>같은 웨이팅에 여러 번 오면 마지막 보고가 유효 (UNCONFIRMED 후 MANUAL 로 갱신 가능)</li>
 *   <li>/topic/pos/{storeId} ARRIVAL_REPORTED {result, alighted, partySize} → POS 배지 초록/회색/노랑</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ArrivalService {

    public ArrivalResponse report(long deviceId, long waitingId, ArrivalReportRequest request) {
        throw BusinessException.notImplemented("Phase 2 · P0-5 하차 확인");
    }
}
