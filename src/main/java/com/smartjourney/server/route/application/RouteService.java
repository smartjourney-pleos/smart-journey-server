package com.smartjourney.server.route.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteRequest;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteResponse;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기능 1.2 코스 순서 제안 (P1-2, 보조 기능). 핵심 루프(P0)는 이 API 없이도 돈다.
 *
 * <ul>
 *   <li>provider=none : 사용자가 고른 순서 그대로 (지금 동작)</li>
 *   <li>provider=kakao : TODO(Phase 3) 카카오모빌리티 다중 경유지/길찾기로 매장↔매장 소요시간 →
 *       {@link com.smartjourney.server.route.domain.PermutationRouteOptimizer}. 실패하면 입력 순서로 폴백</li>
 * </ul>
 * 키는 서버에만 둔다 (앱에 넣지 않음).
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final String PROVIDER_NONE = "none";

    private final AppProperties properties;

    public OptimizeRouteResponse optimize(OptimizeRouteRequest request) {
        List<Long> storeIds = request.storeIds();
        if (new HashSet<>(storeIds).size() != storeIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 매장이 두 번 들어 있습니다.");
        }
        String provider = properties.route().provider();
        if (provider == null || PROVIDER_NONE.equalsIgnoreCase(provider)) {
            return new OptimizeRouteResponse(List.copyOf(storeIds), null, null, false, PROVIDER_NONE);
        }
        throw BusinessException.notImplemented("Phase 3 · P1-2 카카오 소요시간 연동");
    }
}
