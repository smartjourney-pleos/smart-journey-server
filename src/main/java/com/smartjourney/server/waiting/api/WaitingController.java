package com.smartjourney.server.waiting.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.common.security.AuthHeaders;
import com.smartjourney.server.common.security.DevicePrincipal;
import com.smartjourney.server.waiting.api.WaitingDtos.ChangePartySizeRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.CreateWaitingRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.DeferRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.EtaUpdateRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.TicketResponse;
import com.smartjourney.server.waiting.api.WaitingDtos.WaitingResponse;
import com.smartjourney.server.waiting.application.WaitingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. 웨이팅 (차량)", description = "기능 3.1 접수 · 1.3 인원 변경 · 4.2 순번 미루기 · 6.1 ETA")
@SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME)
@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    @Operation(summary = "원격 웨이팅 접수 (골든타임 승인)",
            description = "사용자 승인(터치·음성) 후에만 호출 (정책 2). 같은 Idempotency-Key 재시도는 같은 결과")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitingResponse create(@AuthenticationPrincipal DevicePrincipal device,
                                  @RequestHeader(AuthHeaders.IDEMPOTENCY_KEY) @Size(min = 8, max = 64) String idempotencyKey,
                                  @Valid @RequestBody CreateWaitingRequest request) {
        return waitingService.create(device.deviceId(), idempotencyKey, request);
    }

    @Operation(summary = "웨이팅 상태", description = "STOMP /topic/waitings/{id} 재연결 시 동기화용")
    @GetMapping("/{waitingId}")
    public WaitingResponse get(@AuthenticationPrincipal DevicePrincipal device, @PathVariable long waitingId) {
        return waitingService.get(device.deviceId(), waitingId);
    }

    @Operation(summary = "인원 변경 (P1)")
    @PatchMapping("/{waitingId}/party-size")
    public WaitingResponse changePartySize(@AuthenticationPrincipal DevicePrincipal device,
                                           @PathVariable long waitingId,
                                           @Valid @RequestBody ChangePartySizeRequest request) {
        return waitingService.changePartySize(device.deviceId(), waitingId, request);
    }

    @Operation(summary = "순번 미루기 (P2)", description = "횟수 초과 시 409 DEFER_LIMIT_EXCEEDED")
    @PostMapping("/{waitingId}/defer")
    public WaitingResponse defer(@AuthenticationPrincipal DevicePrincipal device,
                                 @PathVariable long waitingId,
                                 @Valid @RequestBody DeferRequest request) {
        return waitingService.defer(device.deviceId(), waitingId, request);
    }

    @Operation(summary = "남은 ETA 전달", description = "좌표 대신 분 단위 ETA 만 (정책 4)")
    @PutMapping("/{waitingId}/eta")
    public WaitingResponse updateEta(@AuthenticationPrincipal DevicePrincipal device,
                                     @PathVariable long waitingId,
                                     @Valid @RequestBody EtaUpdateRequest request) {
        return waitingService.updateEta(device.deviceId(), waitingId, request);
    }

    @Operation(summary = "번호표 QR (호출 후)")
    @GetMapping("/{waitingId}/ticket")
    public TicketResponse ticket(@AuthenticationPrincipal DevicePrincipal device, @PathVariable long waitingId) {
        return waitingService.ticket(device.deviceId(), waitingId);
    }

    @Operation(summary = "웨이팅 취소")
    @DeleteMapping("/{waitingId}")
    public WaitingResponse cancel(@AuthenticationPrincipal DevicePrincipal device, @PathVariable long waitingId) {
        return waitingService.cancel(device.deviceId(), waitingId);
    }
}
