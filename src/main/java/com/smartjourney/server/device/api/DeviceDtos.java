package com.smartjourney.server.device.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    @Schema(description = "동의하고 기기 토큰 발급")
    public record RegisterDeviceRequest(
            @Schema(description = "사용자가 본 고지 문구 버전", example = "2026-11-v1")
            @NotBlank String consentVersion
    ) {
    }

    public record RegisterDeviceResponse(
            @Schema(description = "이후 모든 요청의 X-Device-Token 헤더 값. DataStore 에 암호화해 보관")
            String deviceToken,
            String consentVersion,
            Instant consentedAt
    ) {
    }

    public record ReconsentRequest(@NotBlank String consentVersion) {
    }

    public record DeviceStatusResponse(
            String consentVersion,
            Instant consentedAt,
            Instant revokedAt,
            boolean consentActive,
            @Schema(description = "서버의 현재 고지 버전과 다르면 재동의 화면을 띄운다")
            boolean reconsentRequired
    ) {
    }
}
