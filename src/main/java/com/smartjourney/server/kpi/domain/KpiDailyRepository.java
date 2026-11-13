package com.smartjourney.server.kpi.domain;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KpiDailyRepository extends JpaRepository<KpiDaily, LocalDate> {
}
