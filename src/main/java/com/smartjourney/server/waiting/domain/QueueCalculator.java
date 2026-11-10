package com.smartjourney.server.waiting.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 대기시간 산출 (개발 명세서 6.1). 순수 함수 — 단위 테스트 대상.
 *
 * <pre>
 * estimatedWaitMin = manualWaitMin ?: (aheadTeams × minutesPerTeam)
 * </pre>
 *
 * 골든타임 판단(남은 ETA ≤ 예상 대기 − 도착 여유)은 차량 앱 domain 모듈이 한다. 서버는 입력값만 정확히 준다.
 */
public final class QueueCalculator {

    private QueueCalculator() {
    }

    public static int estimatedWaitMin(Integer manualWaitMin, int aheadTeams, int minutesPerTeam) {
        if (aheadTeams < 0 || minutesPerTeam < 0) {
            throw new IllegalArgumentException("aheadTeams and minutesPerTeam must be >= 0");
        }
        if (manualWaitMin != null) {
            return Math.max(0, manualWaitMin);
        }
        return aheadTeams * minutesPerTeam;
    }

    public static Instant estimatedEntryAt(Instant now, int estimatedWaitMin) {
        return now.plus(Duration.ofMinutes(Math.max(0, estimatedWaitMin)));
    }

    /**
     * 새 팀의 queue_order. 등록 시각(ms) × 1000 으로 두어 미루기 때 사이에 끼워 넣을 여유를 둔다.
     */
    public static long initialQueueOrder(Instant registeredAt) {
        return registeredAt.toEpochMilli() * 1_000L;
    }
}
