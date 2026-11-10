package com.smartjourney.server.store.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class StoreDtos {

    private StoreDtos() {
    }

    @Schema(description = "방문지 고르기(V-04)용 매장 카드. 좌표·POI 는 NaviHelper RouteInfo 에 그대로 넣는다")
    public record StoreSummary(
            long id,
            String name,
            String address,
            double latitude,
            double longitude,
            String poiId,
            String poiSubId,
            boolean openNow,
            int avgStayMin,
            QueueStatusResponse queue
    ) {
    }

    @Schema(description = "골든타임 계산 입력값. STOMP QUEUE_UPDATED 의 payload 와 같은 모양")
    public record QueueStatusResponse(
            long storeId,
            @Schema(description = "대기 중(REGISTERED·ARRIVED) 팀 수") int aheadTeams,
            @Schema(description = "manualWaitMin ?: aheadTeams × minutesPerTeam") int estimatedWaitMin,
            @Schema(description = "도착 여유(분). 골든타임 = 남은 ETA ≤ estimatedWaitMin − arrivalBufferMin")
            int arrivalBufferMin,
            @Schema(description = "점주가 대기시간을 직접 넣었는지") boolean manualOverride,
            Instant calculatedAt
    ) {
    }
}
