package com.smartjourney.server.privacy.application;

import com.smartjourney.server.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보유 기간이 지난 데이터 파기 (고지한 retention-days, 기본 30일).
 *
 * TODO(Phase 4):
 * <ul>
 *   <li>closed_at 이 보유 기간보다 오래된 waiting → arrival_check, waiting_event 삭제 후 waiting 삭제</li>
 *   <li>ENDED journey → journey_event, journey_stop, journey 삭제</li>
 *   <li>철회 후 보유 기간이 지난 device 삭제</li>
 *   <li>idempotency_record 는 24시간 지나면 삭제</li>
 *   <li>KPI 는 파기 전에 일 단위 집계 테이블로 옮겨 둔다 (개인 식별 불가 수준)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final AppProperties properties;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void purgeExpired() {
        log.info("[retention] TODO: purge data older than {} days", properties.consent().retentionDays());
    }
}
