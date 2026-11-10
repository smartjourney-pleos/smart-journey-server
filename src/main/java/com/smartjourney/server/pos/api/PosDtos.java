package com.smartjourney.server.pos.api;

import com.smartjourney.server.arrival.domain.ArrivalResult;
import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class PosDtos {

    private PosDtos() {
    }

    // ---------- 로그인 (P-01) ----------

    public record PosStoreOption(long storeId, String name) {
    }

    public record PosLoginRequest(@NotNull Long storeId, @NotBlank String pin) {
    }

    public record PosLoginResponse(String accessToken, Instant expiresAt, long storeId, String storeName) {
    }

    // ---------- 대기열 보드 (P-02) ----------

    @Schema(description = "POS 대기열 보드. 점주에게는 인원·ETA·도착·하차만 보여 준다 (정책 4)")
    public record PosQueueResponse(
            long storeId,
            int aheadTeams,
            int estimatedWaitMin,
            List<PosQueueItem> items
    ) {
    }

    public record PosQueueItem(
            long waitingId,
            int ticketNo,
            int partySize,
            WaitingChannel channel,
            @Schema(description = "🚗 차량 인증 채널 표시 (VEHICLE)") boolean vehicleVerified,
            WaitingStatus status,
            Integer etaMin,
            Instant etaUpdatedAt,
            ArrivalBadge arrival,
            int deferCount,
            Instant registeredAt
    ) {
    }

    @Schema(description = "하차 배지: CONFIRMED 초록 \"M/N명 하차\" / MANUAL 회색 \"고객 확인\" / UNCONFIRMED 노랑 \"인원 미확인\"")
    public record ArrivalBadge(ArrivalResult result, Integer alighted, int partySize, Instant arrivedAt) {
    }

    // ---------- 팀 상세 (P-03) ----------

    public record PosWaitingDetail(PosQueueItem item, List<PosWaitingEvent> events) {
    }

    public record PosWaitingEvent(WaitingEventType type, String payload, Instant occurredAt) {
    }

    // ---------- 설정 (P-05) ----------

    public record PosSettingsResponse(
            int minutesPerTeam,
            int arrivalBufferMin,
            Integer manualWaitMin,
            int maxDefers
    ) {
    }

    @Schema(description = "null 인 항목은 그대로. manualWaitMin 을 지우려면 clearManualWait=true")
    public record UpdatePosSettingsRequest(
            @Min(1) @Max(60) Integer minutesPerTeam,
            @Min(0) @Max(60) Integer arrivalBufferMin,
            @Min(0) @Max(300) Integer manualWaitMin,
            boolean clearManualWait
    ) {
    }

    // ---------- 데모 패널 (P-06) ----------

    @Schema(description = "현장 대기 팀 추가 (심사위원 조작용)")
    public record WalkInRequest(@Min(1) @Max(10) int count, @Min(1) @Max(9) int partySize) {
    }

    public record DemoResult(int changedTeams, int aheadTeams, int estimatedWaitMin) {
    }

    // ---------- 입장 QR 확인 (P2) ----------

    public record VerifyTicketRequest(@NotBlank String qrPayload) {
    }

    public record VerifyTicketResponse(boolean valid, Long waitingId, Integer ticketNo, Integer partySize) {
    }
}
