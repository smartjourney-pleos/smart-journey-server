package com.smartjourney.server.device.api;

import com.smartjourney.server.common.config.OpenApiConfig;
import com.smartjourney.server.common.security.DevicePrincipal;
import com.smartjourney.server.device.api.DeviceDtos.DeviceStatusResponse;
import com.smartjourney.server.device.api.DeviceDtos.ReconsentRequest;
import com.smartjourney.server.device.api.DeviceDtos.RegisterDeviceRequest;
import com.smartjourney.server.device.api.DeviceDtos.RegisterDeviceResponse;
import com.smartjourney.server.device.application.DeviceService;
import com.smartjourney.server.device.domain.ConsentNotice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. 동의·기기", description = "기능 6.3 외부 전송 고지 및 동의 (P0-6)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "고지 문구 조회 (V-01)", description = "인증 불필요. 항목·목적·보유기간·받는 곳·거부 권리")
    @GetMapping("/consent/notice")
    public ConsentNotice notice() {
        return deviceService.currentNotice();
    }

    @Operation(summary = "동의 + 기기 토큰 발급", description = "인증 불필요. 동의 버튼을 누른 경우에만 호출")
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterDeviceResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
        return deviceService.register(request.consentVersion());
    }

    @Operation(summary = "내 동의 상태", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME))
    @GetMapping("/devices/me")
    public DeviceStatusResponse me(@AuthenticationPrincipal DevicePrincipal device) {
        return deviceService.status(device.deviceId());
    }

    @Operation(summary = "재동의 / 고지 버전 갱신", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME))
    @PutMapping("/devices/me/consent")
    public DeviceStatusResponse reconsent(@AuthenticationPrincipal DevicePrincipal device,
                                          @Valid @RequestBody ReconsentRequest request) {
        return deviceService.reconsent(device.deviceId(), request.consentVersion());
    }

    @Operation(summary = "동의 철회", description = "이후 외부 전송 API 는 403 CONSENT_REQUIRED",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_SCHEME))
    @DeleteMapping("/devices/me/consent")
    public DeviceStatusResponse revoke(@AuthenticationPrincipal DevicePrincipal device) {
        return deviceService.revoke(device.deviceId());
    }
}
