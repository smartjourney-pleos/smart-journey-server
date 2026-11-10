package com.smartjourney.server.journey.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.common.security.DevicePrincipal;
import com.smartjourney.server.journey.api.JourneyDtos.ChangePartyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.CreateJourneyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyEventRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyResponse;
import com.smartjourney.server.journey.api.JourneyDtos.ReorderStopsRequest;
import com.smartjourney.server.journey.application.JourneyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. 여정(코스)", description = "기능 1.1 · 1.2 · 1.3 — 여정 시작하면 JourneyService(앱)가 뒤에서 돈다")
@SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME)
@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;

    @Operation(summary = "여정 시작", description = "인원(센서 제안 or 수정값) + 방문 매장 1~3곳(내비 입력 순서)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JourneyResponse create(@AuthenticationPrincipal DevicePrincipal device,
                                  @Valid @RequestBody CreateJourneyRequest request) {
        return journeyService.create(device.deviceId(), request);
    }

    @Operation(summary = "진행 중 여정 (앱 복구용)", description = "없으면 204")
    @GetMapping("/current")
    public ResponseEntity<JourneyResponse> current(@AuthenticationPrincipal DevicePrincipal device) {
        return journeyService.current(device.deviceId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "여정 진행 현황 (V-06)")
    @GetMapping("/{journeyId}")
    public JourneyResponse get(@AuthenticationPrincipal DevicePrincipal device, @PathVariable long journeyId) {
        return journeyService.get(device.deviceId(), journeyId);
    }

    @Operation(summary = "여정 인원 변경 (정차 중 수정)")
    @PatchMapping("/{journeyId}/party")
    public JourneyResponse changeParty(@AuthenticationPrincipal DevicePrincipal device,
                                       @PathVariable long journeyId,
                                       @Valid @RequestBody ChangePartyRequest request) {
        return journeyService.changeParty(device.deviceId(), journeyId, request);
    }

    @Operation(summary = "코스 순서 변경 (P2)", description = "승인 후에만 호출 (정책 2). 앱은 이후 requestRouteWithWaypoints 재호출")
    @PutMapping("/{journeyId}/stops")
    public JourneyResponse reorderStops(@AuthenticationPrincipal DevicePrincipal device,
                                        @PathVariable long journeyId,
                                        @Valid @RequestBody ReorderStopsRequest request) {
        return journeyService.reorderStops(device.deviceId(), journeyId, request);
    }

    @Operation(summary = "여정 끝내기")
    @PostMapping("/{journeyId}/end")
    public JourneyResponse end(@AuthenticationPrincipal DevicePrincipal device, @PathVariable long journeyId) {
        return journeyService.end(device.deviceId(), journeyId);
    }

    @Operation(summary = "차량 쪽 이벤트 보고 (KPI, P1)", description = "승인 카드 노출·거절, 인원 제안 수락·수정 등")
    @PostMapping("/{journeyId}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordEvent(@AuthenticationPrincipal DevicePrincipal device,
                            @PathVariable long journeyId,
                            @Valid @RequestBody JourneyEventRequest request) {
        journeyService.recordEvent(device.deviceId(), journeyId, request);
    }
}
