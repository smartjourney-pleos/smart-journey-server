package com.smartjourney.server.kpi.api;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.kpi.api.KpiDtos.KpiRangeResponse;
import com.smartjourney.server.kpi.application.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "9. 운영 지표", description = "PRD 핵심 지표 일 집계. 헤더 X-Ops-Token (환경변수 OPS_TOKEN). 대시보드: /ops/kpi.html")
@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    public static final String OPS_TOKEN_HEADER = "X-Ops-Token";

    private final KpiService kpiService;
    private final AppProperties properties;

    @Operation(summary = "KPI 일 집계", description = "기본: 오늘 포함 최근 7일. 최대 92일")
    @GetMapping("/kpi/daily")
    public KpiRangeResponse daily(
            @Parameter(hidden = true) @RequestHeader(name = OPS_TOKEN_HEADER, required = false) String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireOps(token);
        return kpiService.range(from, to);
    }

    private void requireOps(String token) {
        String expected = properties.ops() == null ? null : properties.ops().token();
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "운영 지표 API 가 꺼져 있습니다 (OPS_TOKEN 미설정).");
        }
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
