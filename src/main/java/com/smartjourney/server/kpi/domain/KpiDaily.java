package com.smartjourney.server.kpi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * KPI 일 집계 스냅샷. 원본 기록 파기 전에 남기는 하루치 숫자 (개인 식별 불가).
 */
@Getter
@Entity
@Table(name = "kpi_daily")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KpiDaily {

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static KpiDaily of(LocalDate date, String payload, Instant now) {
        KpiDaily k = new KpiDaily();
        k.businessDate = date;
        k.payload = payload;
        k.createdAt = now;
        return k;
    }
}
