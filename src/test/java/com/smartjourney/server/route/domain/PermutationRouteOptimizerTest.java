package com.smartjourney.server.route.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;
import org.junit.jupiter.api.Test;

class PermutationRouteOptimizerTest {

    // 매장 1 ↔ 2: 10분, 2 ↔ 3: 10분, 1 ↔ 3: 30분
    private final ToIntBiFunction<Long, Long> leg = (a, b) -> {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        if (lo == 1 && hi == 2) {
            return 10;
        }
        if (lo == 2 && hi == 3) {
            return 10;
        }
        return 30;
    };

    @Test
    void picksShortestOrder() {
        // 현재 위치에서 3이 가장 가깝다 → 3 → 2 → 1 (5 + 10 + 10 = 25)
        var result = PermutationRouteOptimizer.optimize(
                List.of(1L, 3L, 2L), Map.of(1L, 40, 2L, 20, 3L, 5), leg);
        assertThat(result.orderedStoreIds()).containsExactly(3L, 2L, 1L);
        assertThat(result.totalTravelMin()).isEqualTo(25);
        assertThat(result.legsMin()).containsExactly(5, 10, 10);
    }

    @Test
    void keepsUserOrderOnTie() {
        var result = PermutationRouteOptimizer.optimize(List.of(2L, 1L), Map.of(), (a, b) -> 10);
        assertThat(result.orderedStoreIds()).containsExactly(2L, 1L);
    }

    @Test
    void singleStop() {
        var result = PermutationRouteOptimizer.optimize(List.of(7L), Map.of(7L, 12), leg);
        assertThat(result.orderedStoreIds()).containsExactly(7L);
        assertThat(result.totalTravelMin()).isEqualTo(12);
    }
}
