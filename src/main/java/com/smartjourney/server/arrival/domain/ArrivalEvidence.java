package com.smartjourney.server.arrival.domain;

import java.time.Instant;

/**
 * 하차 확인 근거 (모두 선택). 차량 앱 AlightDetector 상태 머신의 각 단계 시각.
 */
public record ArrivalEvidence(
        Instant arrivedAt,          // onWayPointArrived / onDestinationArrived 수신
        Instant doorOpenedAt,       // 승객 문(ROW_n) 하나라도 열림
        Instant doorLockedAt,       // (선택) 전체 잠김
        Instant beltReleasedAt,     // (선택) 벨트 풀림
        Instant settledAt,          // 안정화 5초 완료
        Integer occupancyBefore,
        Integer occupancyAfter
) {

    public static ArrivalEvidence empty() {
        return new ArrivalEvidence(null, null, null, null, null, null, null);
    }
}
