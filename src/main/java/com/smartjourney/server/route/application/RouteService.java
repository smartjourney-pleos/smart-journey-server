package com.smartjourney.server.route.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteRequest;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteResponse;
import com.smartjourney.server.route.domain.PermutationRouteOptimizer;
import com.smartjourney.server.route.domain.TravelTimeProvider;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기능 1.2 코스 순서 제안 (P1-2, 보조 기능). 핵심 루프(P0)는 이 API 없이도 돈다.
 *
 * <ul>
 *   <li>provider=none : 사용자가 고른 순서 그대로</li>
 *   <li>provider=kakao : 카카오모빌리티로 매장↔매장 소요시간 → {@link PermutationRouteOptimizer} (최대 3! = 6가지).
 *       첫 구간(현재 위치 → 매장)은 앱이 NaviHelper requestTrip 으로 구한 분 값(firstLegMin)만 받는다.
 *       키가 없거나 외부 API 가 실패하면 입력 순서로 폴백 (optimized=false, provider=none)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    static final String PROVIDER_NONE = "none";
    static final String PROVIDER_KAKAO = "kakao";

    private final AppProperties properties;
    private final StoreRepository storeRepository;
    private final List<TravelTimeProvider> providers;

    public OptimizeRouteResponse optimize(OptimizeRouteRequest request) {
        List<Long> storeIds = request.storeIds();
        if (new HashSet<>(storeIds).size() != storeIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 매장이 두 번 들어 있습니다.");
        }
        Map<Long, Store> stores = storeRepository.findAllById(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        if (stores.size() != storeIds.size()) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }

        String provider = properties.route().provider();
        if (provider == null || PROVIDER_NONE.equalsIgnoreCase(provider) || storeIds.size() == 1) {
            return asIs(storeIds);
        }
        TravelTimeProvider travel = providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(provider))
                .findFirst()
                .orElse(null);
        if (travel == null) {
            log.warn("[route] unknown provider '{}' → 입력 순서 유지", provider);
            return asIs(storeIds);
        }

        try {
            Map<String, Integer> legs = new HashMap<>();
            for (Long a : storeIds) {
                for (Long b : storeIds) {
                    if (!a.equals(b)) {
                        Store from = stores.get(a);
                        Store to = stores.get(b);
                        legs.put(a + ">" + b, travel.minutes(from.getLatitude(), from.getLongitude(),
                                to.getLatitude(), to.getLongitude()));
                    }
                }
            }
            Map<Long, Integer> firstLeg = request.firstLegMin() == null ? Map.of() : request.firstLegMin();
            PermutationRouteOptimizer.Result result = PermutationRouteOptimizer.optimize(
                    storeIds, firstLeg, (a, b) -> legs.get(a + ">" + b));
            return new OptimizeRouteResponse(result.orderedStoreIds(), result.legsMin(), result.totalTravelMin(),
                    true, travel.name());
        } catch (RuntimeException e) {
            log.warn("[route] {} 소요시간 조회 실패 → 입력 순서 유지 ({})", travel.name(), e.getMessage());
            return asIs(storeIds);
        }
    }

    private OptimizeRouteResponse asIs(List<Long> storeIds) {
        return new OptimizeRouteResponse(List.copyOf(storeIds), null, null, false, PROVIDER_NONE);
    }
}
