package com.smartjourney.server.route.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteRequest;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteResponse;
import com.smartjourney.server.route.application.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. 코스 순서 제안", description = "기능 1.2 (P1, 보조) — 키 없으면 입력 순서 그대로")
@SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME)
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @Operation(summary = "방문 순서 제안", description = "결과 순서를 사용자가 승인하면 앱이 requestRouteWithWaypoints 로 내비에 입력")
    @PostMapping("/optimize")
    public OptimizeRouteResponse optimize(@Valid @RequestBody OptimizeRouteRequest request) {
        return routeService.optimize(request);
    }
}
