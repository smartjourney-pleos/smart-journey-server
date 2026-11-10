package com.smartjourney.server.route.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;

/**
 * 방문 순서 최적화 (기능 1.2, P1-2). 방문지가 최대 3곳이라 전수 비교(최대 3! = 6가지)로 충분하다.
 * 순수 함수 — 단위 테스트 대상.
 *
 * <p><b>정책 4 (좌표 미전송) 를 지키는 방법</b>: 차량의 현재 위치는 서버로 오지 않는다.
 * 차량 앱이 NaviHelper {@code requestTrip(TripRequest)} → {@code onTripInfo(TripInfo.eta 초)} 로
 * "지금 위치 → 각 후보 매장" 첫 구간 시간만 구해서 분 단위로 보낸다({@code firstLegMin}).
 * 매장 ↔ 매장 시간은 서버가 매장 좌표로 계산한다(카카오모빌리티, 서버에만 키 보관).
 *
 * <p>비용 = 이동 시간 합. 같으면 사용자가 고른 원래 순서를 유지한다.
 * TODO(P1-2): 영업시간(도착 예정 시각에 열려 있는지)·체류시간·예상 대기 반영
 */
public final class PermutationRouteOptimizer {

    private PermutationRouteOptimizer() {
    }

    /**
     * @param storeIds   사용자가 고른 순서 (1..3)
     * @param firstLegMin 현재 위치 → 매장 (분). 없는 매장은 0 으로 본다
     * @param legMin      매장 a → 매장 b (분)
     */
    public static Result optimize(List<Long> storeIds, Map<Long, Integer> firstLegMin,
                                  ToIntBiFunction<Long, Long> legMin) {
        if (storeIds.isEmpty() || storeIds.size() > 3) {
            throw new IllegalArgumentException("1..3 stops");
        }
        List<List<Long>> candidates = new ArrayList<>();
        permute(new ArrayList<>(storeIds), 0, candidates);

        List<Long> best = storeIds;
        int bestCost = cost(storeIds, firstLegMin, legMin);
        for (List<Long> candidate : candidates) {
            int c = cost(candidate, firstLegMin, legMin);
            if (c < bestCost) {
                best = candidate;
                bestCost = c;
            }
        }
        return new Result(List.copyOf(best), bestCost, legsOf(best, firstLegMin, legMin));
    }

    static int cost(List<Long> order, Map<Long, Integer> firstLegMin, ToIntBiFunction<Long, Long> legMin) {
        return legsOf(order, firstLegMin, legMin).stream().mapToInt(Integer::intValue).sum();
    }

    private static List<Integer> legsOf(List<Long> order, Map<Long, Integer> firstLegMin,
                                        ToIntBiFunction<Long, Long> legMin) {
        List<Integer> legs = new ArrayList<>();
        legs.add(firstLegMin.getOrDefault(order.get(0), 0));
        for (int i = 1; i < order.size(); i++) {
            legs.add(legMin.applyAsInt(order.get(i - 1), order.get(i)));
        }
        return legs;
    }

    private static void permute(List<Long> items, int k, List<List<Long>> out) {
        if (k == items.size()) {
            out.add(new ArrayList<>(items));
            return;
        }
        for (int i = k; i < items.size(); i++) {
            java.util.Collections.swap(items, k, i);
            permute(items, k + 1, out);
            java.util.Collections.swap(items, k, i);
        }
    }

    /** legsMin[0] = 현재 위치 → 첫 매장, legsMin[i] = i-1 번째 → i 번째 */
    public record Result(List<Long> orderedStoreIds, int totalTravelMin, List<Integer> legsMin) {
    }
}
