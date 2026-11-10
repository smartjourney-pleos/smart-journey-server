package com.smartjourney.server.waiting.api;

import com.smartjourney.server.waiting.domain.ApprovalMethod;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class WaitingDtos {

    private WaitingDtos() {
    }

    @Schema(description = "골든타임 승인 → 원격 웨이팅 접수 (기능 3.1). 헤더 Idempotency-Key 필수")
    public record CreateWaitingRequest(
            @NotNull Long journeyId,
            @NotNull Long storeId,
            @Min(1) @Max(9) int partySize,
            @NotNull ApprovalMethod approvalMethod
    ) {
    }

    @Schema(description = "차량용 웨이팅 상태. STOMP WAITING_UPDATED payload 와 같은 모양")
    public record WaitingResponse(
            long waitingId,
            long storeId,
            String storeName,
            int ticketNo,
            int partySize,
            WaitingStatus status,
            @Schema(description = "내 앞 팀 수 (CALLED 이후 0)") int aheadTeams,
            int estimatedWaitMin,
            Instant estimatedEntryAt,
            int deferCount,
            int maxDefers,
            Integer etaMin
    ) {
    }

    @Schema(description = "다음 매장 인원 변경 (기능 1.3). 승인 후에만 호출. 호출 이후면 409 WAITING_ALREADY_CALLED")
    public record ChangePartySizeRequest(@Min(1) @Max(9) int partySize) {
    }

    @Schema(description = "순번 미루기 (기능 4.2). delayMin 으로 몇 팀 뒤로 갈지 정한다")
    public record DeferRequest(@Min(0) @Max(180) int delayMin) {
    }

    @Schema(description = "남은 ETA(분). NaviHelper 는 초 단위(WaypointInfo.remainTime 등) → 앱이 올림해서 분으로. "
            + "2분 이상 바뀌었을 때 또는 1분 주기로만 전송")
    public record EtaUpdateRequest(@Min(0) @Max(600) int etaMin) {
    }

    @Schema(description = "번호표 QR (V-11). CALLED 이후에만")
    public record TicketResponse(
            long waitingId,
            String storeName,
            int ticketNo,
            int partySize,
            String qrPayload,
            Instant expiresAt
    ) {
    }
}
