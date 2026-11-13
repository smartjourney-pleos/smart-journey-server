package com.smartjourney.server.kpi.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public final class KpiDtos {

    private KpiDtos() {
    }

    @Schema(description = "PRD 핵심 지표 하루치 (Asia/Seoul 영업일). 비율은 0..1, 분모가 0 이면 null")
    public record DailyKpi(
            LocalDate date,
            @Schema(description = "스냅샷(파기 후 보존본)인지, 원본에서 바로 계산했는지") boolean snapshot,
            Journeys journeys,
            Prompts prompts,
            Approvals approvals,
            Arrivals arrivals,
            Outcomes outcomes,
            Driving driving
    ) {
    }

    @Schema(description = "인원 제안 수락률 = 센서 제안 그대로 시작한 여정 / 전체 여정")
    public record Journeys(int started, int sensorAccepted, int manualEdited, Double partySuggestionAcceptRate) {
    }

    @Schema(description = "골든타임 승인 카드. acceptRate = 접수 / 카드 노출")
    public record Prompts(int shown, int declined, int timeout, int registered, Double acceptRate) {
    }

    @Schema(description = "승인 방식 비율 (정책 2: 화면·음성 같은 규칙)")
    public record Approvals(int touch, int voice, Double voiceRate) {
    }

    @Schema(description = "하차 확인 결과 (웨이팅별 마지막 보고). confirmedRate = CONFIRMED / 보고된 웨이팅")
    public record Arrivals(int confirmed, int manual, int unconfirmed, Double confirmedRate) {
    }

    @Schema(description = "차량 채널 웨이팅의 끝 (그날 접수분)")
    public record Outcomes(int seated, int noShow, int cancelled, int stillActive) {
    }

    @Schema(description = "주행 중 조작·음성 (기능 5.x)")
    public record Driving(int voiceCommands, int restrictedActionsBlocked, int delaysDetected, int partyChangeSuggested) {
    }

    public record KpiRangeResponse(LocalDate from, LocalDate to, List<DailyKpi> days) {
    }
}
