package com.smartjourney.server.arrival.api;

import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalReportRequest;
import com.smartjourney.server.arrival.api.ArrivalDtos.ArrivalResponse;
import com.smartjourney.server.arrival.application.ArrivalService;
import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.common.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "5. 도착·하차 확인", description = "기능 6.2 — 차량이 증명하는 도착 (핵심 차별 기능)")
@SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME)
@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class ArrivalController {

    private final ArrivalService arrivalService;

    @Operation(summary = "도착·하차 보고",
            description = "CONFIRMED(자동 확정) / MANUAL(\"도착했어요\") / UNCONFIRMED(인원 미확인). 미확정을 성공으로 보내지 않는다")
    @PostMapping("/{waitingId}/arrival")
    public ArrivalResponse report(@AuthenticationPrincipal DevicePrincipal device,
                                  @PathVariable long waitingId,
                                  @Valid @RequestBody ArrivalReportRequest request) {
        return arrivalService.report(device.deviceId(), waitingId, request);
    }
}
