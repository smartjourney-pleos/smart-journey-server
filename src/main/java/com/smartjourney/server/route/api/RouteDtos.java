package com.smartjourney.server.route.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class RouteDtos {

    private RouteDtos() {
    }

    @Schema(description = "방문 순서 제안 요청. 차량 현재 좌표는 보내지 않는다 (정책 4)")
    public record OptimizeRouteRequest(
            @Schema(description = "사용자가 고른 순서") @NotEmpty @Size(max = 3) List<@NotNull Long> storeIds,
            @Schema(description = "현재 위치 → 매장 소요(분). 앱이 NaviHelper requestTrip → onTripInfo.eta(초)로 계산. 선택")
            Map<Long, Integer> firstLegMin
    ) {
    }

    public record OptimizeRouteResponse(
            List<Long> orderedStoreIds,
            @Schema(description = "legsMin[0] = 현재 위치 → 첫 매장") List<Integer> legsMin,
            Integer totalTravelMin,
            @Schema(description = "false 면 키 없음·API 실패로 입력 순서 그대로") boolean optimized,
            String provider
    ) {
    }
}
