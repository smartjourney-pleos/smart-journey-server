package com.smartjourney.server.journey.api;

import com.smartjourney.server.journey.domain.JourneyEventType;
import com.smartjourney.server.journey.domain.JourneyStatus;
import com.smartjourney.server.journey.domain.PartySource;
import com.smartjourney.server.journey.domain.StopStatus;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JourneyDtos {

    private JourneyDtos() {
    }

    @Schema(description = "여정 시작 (V-05 [여정 시작]). 순서 = 내비 입력 순서 (경유지 FIRST, SECOND, 목적지). "
            + "4곳 이상이면 400 TOO_MANY_STOPS")
    public record CreateJourneyRequest(
            @Min(1) @Max(9) int partySize,
            @NotNull PartySource partySource,
            @NotEmpty List<@NotNull Long> storeIds
    ) {
    }

    public record ChangePartyRequest(
            @Min(1) @Max(9) int partySize,
            @NotNull PartySource partySource
    ) {
    }

    @Schema(description = "코스 순서 변경 (차순위 경유지 선방문 등). storeIds 는 기존 코스 매장의 새 순서")
    public record ReorderStopsRequest(@NotEmpty @Size(max = 3) List<@NotNull Long> storeIds) {
    }

    @Schema(description = "차량 안에서만 일어나는 일 보고 (KPI). 좌표·음성 원문은 넣지 않는다")
    public record JourneyEventRequest(
            @NotNull JourneyEventType type,
            Long storeId,
            Map<String, Object> attributes,
            Instant occurredAt
    ) {
    }

    @Schema(description = "여정 진행 현황 (V-06) · 앱 재시작 시 복구 · Gleo CHECK_WAITING_STATUS 응답 재료")
    public record JourneyResponse(
            long journeyId,
            JourneyStatus status,
            int partySize,
            PartySource partySource,
            Instant startedAt,
            List<StopView> stops
    ) {
    }

    public record StopView(
            int seq,
            long storeId,
            String storeName,
            StopStatus status,
            WaitingBrief waiting
    ) {
    }

    public record WaitingBrief(
            long waitingId,
            int ticketNo,
            WaitingStatus status,
            int partySize,
            int aheadTeams,
            Instant estimatedEntryAt
    ) {
    }
}
