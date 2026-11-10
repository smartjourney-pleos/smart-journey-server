package com.smartjourney.server.arrival.api;

import com.smartjourney.server.arrival.domain.ArrivalEvidence;
import com.smartjourney.server.arrival.domain.ArrivalResult;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ArrivalDtos {

    private ArrivalDtos() {
    }

    @Schema(description = "도착·하차 확인 보고 (기능 6.2 핵심 차별 기능). 인원 수만 — 신원 정보 없음")
    public record ArrivalReportRequest(
            @NotNull ArrivalResult result,
            @Schema(description = "M. CONFIRMED 는 1..N 필수, UNCONFIRMED 는 보내지 않음") @Min(0) @Max(9) Integer alighted,
            @Schema(description = "N (접수 인원)") @Min(1) @Max(9) int partySize,
            @Schema(description = "근거 시각·점유 수 (선택). 좌표 없음") ArrivalEvidence evidence
    ) {
    }

    public record ArrivalResponse(
            long waitingId,
            ArrivalResult result,
            Integer alighted,
            int partySize,
            WaitingStatus waitingStatus,
            Instant reportedAt
    ) {
    }
}
