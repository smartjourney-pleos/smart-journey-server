package com.smartjourney.server.pos.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.common.security.PosPrincipal;
import com.smartjourney.server.pos.api.PosDtos.DemoResult;
import com.smartjourney.server.pos.api.PosDtos.PosQueueResponse;
import com.smartjourney.server.pos.api.PosDtos.PosSettingsResponse;
import com.smartjourney.server.pos.api.PosDtos.PosWaitingDetail;
import com.smartjourney.server.pos.api.PosDtos.UpdatePosSettingsRequest;
import com.smartjourney.server.pos.api.PosDtos.VerifyTicketRequest;
import com.smartjourney.server.pos.api.PosDtos.VerifyTicketResponse;
import com.smartjourney.server.pos.api.PosDtos.WalkInRequest;
import com.smartjourney.server.pos.application.PosDemoService;
import com.smartjourney.server.pos.application.PosQueueService;
import com.smartjourney.server.pos.application.PosSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "8. POS", description = "대기열 보드 · 호출/착석/노쇼 · 설정 · 데모 패널 (기능 6.1)")
@SecurityRequirement(name = OpenApiConfig.POS_SCHEME)
@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosQueueService queueService;
    private final PosSettingsService settingsService;
    private final PosDemoService demoService;

    // ---------- 대기열 ----------

    @Operation(summary = "대기열 보드 (P-02)", description = "STOMP /topic/pos/{storeId} 재연결 시 동기화용")
    @GetMapping("/queue")
    public PosQueueResponse queue(@AuthenticationPrincipal PosPrincipal pos) {
        return queueService.queue(pos.storeId());
    }

    @Operation(summary = "팀 상세 + 이벤트 기록 (P-03)")
    @GetMapping("/waitings/{waitingId}")
    public PosWaitingDetail detail(@AuthenticationPrincipal PosPrincipal pos, @PathVariable long waitingId) {
        return queueService.detail(pos.storeId(), waitingId);
    }

    @Operation(summary = "호출", description = "차량에 CALLED + 번호표 QR 푸시")
    @PostMapping("/waitings/{waitingId}/call")
    public PosWaitingDetail call(@AuthenticationPrincipal PosPrincipal pos, @PathVariable long waitingId) {
        return queueService.call(pos.storeId(), waitingId);
    }

    @Operation(summary = "착석")
    @PostMapping("/waitings/{waitingId}/seat")
    public PosWaitingDetail seat(@AuthenticationPrincipal PosPrincipal pos, @PathVariable long waitingId) {
        return queueService.seat(pos.storeId(), waitingId);
    }

    @Operation(summary = "노쇼")
    @PostMapping("/waitings/{waitingId}/no-show")
    public PosWaitingDetail noShow(@AuthenticationPrincipal PosPrincipal pos, @PathVariable long waitingId) {
        return queueService.noShow(pos.storeId(), waitingId);
    }

    @Operation(summary = "입장 QR 확인 (P2)")
    @PostMapping("/tickets/verify")
    public VerifyTicketResponse verifyTicket(@AuthenticationPrincipal PosPrincipal pos,
                                             @Valid @RequestBody VerifyTicketRequest request) {
        return queueService.verifyTicket(pos.storeId(), request.qrPayload());
    }

    // ---------- 설정 ----------

    @Operation(summary = "매장 설정 조회 (P-05)")
    @GetMapping("/settings")
    public PosSettingsResponse settings(@AuthenticationPrincipal PosPrincipal pos) {
        return settingsService.get(pos.storeId());
    }

    @Operation(summary = "매장 설정 변경", description = "바뀌면 QUEUE_UPDATED → 차량 골든타임 즉시 재계산")
    @PatchMapping("/settings")
    public PosSettingsResponse updateSettings(@AuthenticationPrincipal PosPrincipal pos,
                                              @Valid @RequestBody UpdatePosSettingsRequest request) {
        return settingsService.update(pos.storeId(), request);
    }

    // ---------- 데모 패널 ----------

    @Operation(summary = "데모: 현장 대기 팀 +N (P-06)")
    @PostMapping("/demo/walk-ins")
    public DemoResult addWalkIns(@AuthenticationPrincipal PosPrincipal pos, @Valid @RequestBody WalkInRequest request) {
        return demoService.addWalkIns(pos.storeId(), request.count(), request.partySize());
    }

    @Operation(summary = "데모: 현장 대기 팀 −N")
    @DeleteMapping("/demo/walk-ins")
    public DemoResult removeWalkIns(@AuthenticationPrincipal PosPrincipal pos,
                                    @RequestParam(defaultValue = "1") @Min(1) @Max(10) int count) {
        return demoService.removeWalkIns(pos.storeId(), count);
    }

    @Operation(summary = "데모: 오늘 대기열 초기화 (리허설용)")
    @PostMapping("/demo/reset")
    public DemoResult reset(@AuthenticationPrincipal PosPrincipal pos) {
        return demoService.reset(pos.storeId());
    }
}
