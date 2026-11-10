package com.smartjourney.server.arrival.domain;

import java.util.Optional;

/**
 * 차량이 보낸 하차 확인 값 검증 (정책 7). 순수 함수 — 단위 테스트 대상.
 *
 * <ul>
 *   <li>CONFIRMED : 1 ≤ alighted ≤ partySize 여야 한다 (아무도 안 내렸는데 확정은 모순)</li>
 *   <li>MANUAL    : 사용자가 누른 것. alighted 는 없거나 0..partySize</li>
 *   <li>UNCONFIRMED : alighted 를 보내면 안 된다 (인원 미확인)</li>
 * </ul>
 * 차량 쪽 계산식: {@code M = clamp(partySize - occupiedNow, 0, partySize)} — 운전자가 남으면 M = N-1.
 */
public final class ArrivalPolicy {

    private ArrivalPolicy() {
    }

    /** @return 위반 사유. 문제없으면 empty */
    public static Optional<String> violation(ArrivalResult result, Integer alighted, int partySize) {
        if (result == null) {
            return Optional.of("result is required");
        }
        if (partySize < 1) {
            return Optional.of("partySize must be >= 1");
        }
        return switch (result) {
            case CONFIRMED -> (alighted == null || alighted < 1 || alighted > partySize)
                    ? Optional.of("CONFIRMED requires 1 <= alighted <= partySize")
                    : Optional.empty();
            case MANUAL -> (alighted != null && (alighted < 0 || alighted > partySize))
                    ? Optional.of("MANUAL alighted must be within 0..partySize")
                    : Optional.empty();
            case UNCONFIRMED -> alighted != null
                    ? Optional.of("UNCONFIRMED must not carry alighted")
                    : Optional.empty();
        };
    }

    /** 차량 앱과 같은 식. 서버 쪽 교차 검증·테스트용 */
    public static int alightedFrom(int partySize, int occupiedNow) {
        return Math.max(0, Math.min(partySize, partySize - occupiedNow));
    }
}
