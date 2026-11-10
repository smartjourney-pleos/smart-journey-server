package com.smartjourney.server.store.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.store.api.StoreDtos.QueueStatusResponse;
import com.smartjourney.server.store.api.StoreDtos.StoreSummary;
import com.smartjourney.server.store.application.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. 매장", description = "방문지 고르기 · 골든타임 입력값 (기능 1.2 · 2.1)")
@SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME)
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreQueryService storeQueryService;

    @Operation(summary = "매장 목록 (V-04)", description = "가맹 매장 + 현재 대기. 데모는 가상 매장 2~3곳")
    @GetMapping
    public List<StoreSummary> list() {
        return storeQueryService.list();
    }

    @Operation(summary = "매장 상세")
    @GetMapping("/{storeId}")
    public StoreSummary get(@PathVariable long storeId) {
        return storeQueryService.get(storeId);
    }

    @Operation(summary = "대기 현황 (골든타임 입력값)",
            description = "STOMP /topic/stores/{storeId}/queue 재연결 시 현재 값 동기화용")
    @GetMapping("/{storeId}/queue-status")
    public QueueStatusResponse queueStatus(@PathVariable long storeId) {
        return storeQueryService.queueStatus(storeId);
    }
}
