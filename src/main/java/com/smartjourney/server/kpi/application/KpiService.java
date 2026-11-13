package com.smartjourney.server.kpi.application;

import com.smartjourney.server.arrival.domain.ArrivalCheck;
import com.smartjourney.server.arrival.domain.ArrivalCheckRepository;
import com.smartjourney.server.arrival.domain.ArrivalResult;
import com.smartjourney.server.common.config.ClockConfig;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.util.JsonCodec;
import com.smartjourney.server.journey.domain.Journey;
import com.smartjourney.server.journey.domain.JourneyEvent;
import com.smartjourney.server.journey.domain.JourneyEventRepository;
import com.smartjourney.server.journey.domain.JourneyEventType;
import com.smartjourney.server.journey.domain.JourneyRepository;
import com.smartjourney.server.journey.domain.PartySource;
import com.smartjourney.server.kpi.api.KpiDtos.Approvals;
import com.smartjourney.server.kpi.api.KpiDtos.Arrivals;
import com.smartjourney.server.kpi.api.KpiDtos.DailyKpi;
import com.smartjourney.server.kpi.api.KpiDtos.Driving;
import com.smartjourney.server.kpi.api.KpiDtos.Journeys;
import com.smartjourney.server.kpi.api.KpiDtos.KpiRangeResponse;
import com.smartjourney.server.kpi.api.KpiDtos.Outcomes;
import com.smartjourney.server.kpi.api.KpiDtos.Prompts;
import com.smartjourney.server.kpi.domain.KpiDaily;
import com.smartjourney.server.kpi.domain.KpiDailyRepository;
import com.smartjourney.server.waiting.domain.ApprovalMethod;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRD 핵심 지표 일 집계 (3-5). 데모 규모라 하루치를 읽어 Java 로 센다.
 * 보유 기간이 지나 원본이 파기된 날은 kpi_daily 스냅샷을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    static final int MAX_RANGE_DAYS = 92;

    private final JourneyRepository journeyRepository;
    private final JourneyEventRepository journeyEventRepository;
    private final WaitingRepository waitingRepository;
    private final ArrivalCheckRepository arrivalCheckRepository;
    private final KpiDailyRepository kpiDailyRepository;
    private final JsonCodec json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public KpiRangeResponse range(LocalDate from, LocalDate to) {
        LocalDate today = today();
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "기간은 1~" + MAX_RANGE_DAYS + "일로 넣어 주세요.");
        }
        List<DailyKpi> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(day(d));
        }
        return new KpiRangeResponse(start, end, days);
    }

    @Transactional(readOnly = true)
    public DailyKpi day(LocalDate date) {
        return kpiDailyRepository.findById(date)
                .map(k -> withSnapshotFlag(json.read(k.getPayload(), DailyKpi.class)))
                .orElseGet(() -> compute(date));
    }

    /** 파기 전에 그날 숫자를 남긴다 (이미 있으면 그대로) */
    @Transactional
    public void snapshot(Collection<LocalDate> dates) {
        for (LocalDate d : dates) {
            if (!kpiDailyRepository.existsById(d)) {
                kpiDailyRepository.save(KpiDaily.of(d, json.write(compute(d)), clock.instant()));
            }
        }
    }

    public static LocalDate businessDateOf(Instant instant) {
        return LocalDate.ofInstant(instant, ClockConfig.BUSINESS_ZONE);
    }

    // ------------------------------------------------------------------

    DailyKpi compute(LocalDate date) {
        Instant from = date.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();

        List<Journey> journeys = journeyRepository.findByStartedAtGreaterThanEqualAndStartedAtLessThan(from, to);
        int sensor = count(journeys, j -> j.getPartySource() == PartySource.SENSOR);
        Journeys j = new Journeys(journeys.size(), sensor, journeys.size() - sensor, rate(sensor, journeys.size()));

        Map<JourneyEventType, Integer> ev = new HashMap<>();
        for (JourneyEvent e : journeyEventRepository.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(from, to)) {
            ev.merge(e.getType(), 1, Integer::sum);
        }

        List<Waiting> vehicle = waitingRepository.findByRegisteredAtGreaterThanEqualAndRegisteredAtLessThan(from, to)
                .stream().filter(w -> w.getChannel() == WaitingChannel.VEHICLE).toList();
        int shown = ev.getOrDefault(JourneyEventType.PROMPT_SHOWN, 0);
        Prompts prompts = new Prompts(shown, ev.getOrDefault(JourneyEventType.PROMPT_DECLINED, 0),
                ev.getOrDefault(JourneyEventType.PROMPT_TIMEOUT, 0), vehicle.size(),
                shown == 0 ? null : Math.min(1.0, (double) vehicle.size() / shown));

        int touch = count(vehicle, w -> w.getApprovalMethod() == ApprovalMethod.TOUCH);
        int voice = count(vehicle, w -> w.getApprovalMethod() == ApprovalMethod.VOICE);
        Approvals approvals = new Approvals(touch, voice, rate(voice, touch + voice));

        Map<Long, ArrivalResult> latest = new HashMap<>();
        arrivalCheckRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to).stream()
                .sorted(java.util.Comparator.comparing(ArrivalCheck::getCreatedAt).thenComparing(ArrivalCheck::getId))
                .forEach(a -> latest.put(a.getWaitingId(), a.getResult()));
        int confirmed = (int) latest.values().stream().filter(r -> r == ArrivalResult.CONFIRMED).count();
        int manual = (int) latest.values().stream().filter(r -> r == ArrivalResult.MANUAL).count();
        int unconfirmed = (int) latest.values().stream().filter(r -> r == ArrivalResult.UNCONFIRMED).count();
        Arrivals arrivals = new Arrivals(confirmed, manual, unconfirmed, rate(confirmed, latest.size()));

        Outcomes outcomes = new Outcomes(
                count(vehicle, w -> w.getStatus() == WaitingStatus.SEATED),
                count(vehicle, w -> w.getStatus() == WaitingStatus.NO_SHOW),
                count(vehicle, w -> w.getStatus() == WaitingStatus.CANCELLED),
                count(vehicle, w -> w.getStatus().isActive()));

        Driving driving = new Driving(ev.getOrDefault(JourneyEventType.VOICE_COMMAND, 0),
                ev.getOrDefault(JourneyEventType.RESTRICTED_ACTION_BLOCKED, 0),
                ev.getOrDefault(JourneyEventType.DELAY_DETECTED, 0),
                ev.getOrDefault(JourneyEventType.PARTY_CHANGE_SUGGESTED, 0));

        return new DailyKpi(date, false, j, prompts, approvals, arrivals, outcomes, driving);
    }

    private LocalDate today() {
        return businessDateOf(clock.instant());
    }

    private static DailyKpi withSnapshotFlag(DailyKpi k) {
        return new DailyKpi(k.date(), true, k.journeys(), k.prompts(), k.approvals(), k.arrivals(), k.outcomes(),
                k.driving());
    }

    private static <T> int count(List<T> items, Predicate<T> p) {
        return (int) items.stream().filter(p).count();
    }

    private static Double rate(int part, int whole) {
        return whole == 0 ? null : Math.round(1000.0 * part / whole) / 1000.0;
    }
}
