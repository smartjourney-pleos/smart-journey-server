package com.smartjourney.server.privacy.application;

import com.smartjourney.server.arrival.domain.ArrivalCheckRepository;
import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.device.domain.DeviceRepository;
import com.smartjourney.server.journey.domain.Journey;
import com.smartjourney.server.journey.domain.JourneyEventRepository;
import com.smartjourney.server.journey.domain.JourneyRepository;
import com.smartjourney.server.journey.domain.JourneyStatus;
import com.smartjourney.server.kpi.application.KpiService;
import com.smartjourney.server.waiting.domain.IdempotencyRecordRepository;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingEventRepository;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 기간이 지난 데이터 파기 (고지한 retention-days, 기본 30일 · 정책 4 · 4-8). 매일 04:30 (Asia/Seoul).
 *
 * <ol>
 *   <li>파기 대상 날짜의 KPI 를 kpi_daily 에 먼저 남긴다 (개인 식별 불가 숫자만)</li>
 *   <li>끝난 지(closed_at) 보유 기간이 지난 웨이팅 → arrival_check · waiting_event · waiting</li>
 *   <li>끝난 지(ended_at) 보유 기간이 지났고 웨이팅도 모두 파기 대상인 여정 → journey_event · journey_stop · journey</li>
 *   <li>Idempotency 기록은 24시간 뒤</li>
 *   <li>철회한 지 보유 기간이 지났고 남은 여정·웨이팅이 없는 기기</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final AppProperties properties;
    private final Clock clock;
    private final WaitingRepository waitingRepository;
    private final WaitingEventRepository waitingEventRepository;
    private final ArrivalCheckRepository arrivalCheckRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyEventRepository journeyEventRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final DeviceRepository deviceRepository;
    private final KpiService kpiService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        PurgeResult r = purgeExpired();
        log.info("[retention] purged waitings={} journeys={} devices={} idempotency={} (older than {} days)",
                r.waitings(), r.journeys(), r.devices(), r.idempotency(), properties.consent().retentionDays());
    }

    @Transactional
    public PurgeResult purgeExpired() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(properties.consent().retentionDays()));

        List<Long> waitingIds = waitingRepository.findClosedIdsBefore(cutoff);
        Set<Long> purgeWaitings = new HashSet<>(waitingIds);

        List<Long> journeyIds = new ArrayList<>();
        for (Long journeyId : journeyRepository.findEndedIdsBefore(JourneyStatus.ENDED, cutoff)) {
            boolean allExpired = waitingRepository.findByJourneyId(journeyId).stream()
                    .allMatch(w -> purgeWaitings.contains(w.getId()));
            if (allExpired) {
                journeyIds.add(journeyId);
            }
        }

        // 1) 지표 먼저
        Set<LocalDate> dates = new TreeSet<>();
        waitingRepository.findAllById(waitingIds).stream().map(Waiting::getRegisteredAt)
                .map(KpiService::businessDateOf).forEach(dates::add);
        journeyRepository.findAllById(journeyIds).stream().map(Journey::getStartedAt)
                .map(KpiService::businessDateOf).forEach(dates::add);
        kpiService.snapshot(dates);

        // 2) 웨이팅
        if (!waitingIds.isEmpty()) {
            arrivalCheckRepository.deleteByWaitingIds(waitingIds);
            waitingEventRepository.deleteByWaitingIds(waitingIds);
            waitingRepository.deleteAllByIdInBatch(waitingIds);
        }
        // 3) 여정
        if (!journeyIds.isEmpty()) {
            journeyEventRepository.deleteByJourneyIds(journeyIds);
            journeyRepository.deleteStopsByJourneyIds(journeyIds);
            journeyRepository.deleteAllByIdInBatch(journeyIds);
        }
        // 4) 멱등 기록
        int idem = idempotencyRepository.deleteCreatedBefore(now.minus(IDEMPOTENCY_TTL));

        // 5) 철회 기기
        int devices = 0;
        for (Long deviceId : deviceRepository.findRevokedIdsBefore(cutoff)) {
            if (!journeyRepository.existsByDeviceId(deviceId) && !waitingRepository.existsByDeviceId(deviceId)) {
                idempotencyRepository.deleteByDevice(deviceId);
                deviceRepository.deleteById(deviceId);
                devices++;
            }
        }
        return new PurgeResult(waitingIds.size(), journeyIds.size(), devices, idem);
    }

    public record PurgeResult(int waitings, int journeys, int devices, int idempotency) {
    }
}
