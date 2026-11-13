package com.smartjourney.server.journey.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.util.JsonCodec;
import com.smartjourney.server.device.domain.DeviceRepository;
import com.smartjourney.server.journey.api.JourneyDtos.ChangePartyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.CreateJourneyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyEventRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyResponse;
import com.smartjourney.server.journey.api.JourneyDtos.ReorderStopsRequest;
import com.smartjourney.server.journey.api.JourneyDtos.StopView;
import com.smartjourney.server.journey.api.JourneyDtos.WaitingBrief;
import com.smartjourney.server.journey.domain.Journey;
import com.smartjourney.server.journey.domain.JourneyEvent;
import com.smartjourney.server.journey.domain.JourneyEventRepository;
import com.smartjourney.server.journey.domain.JourneyRepository;
import com.smartjourney.server.journey.domain.JourneyStatus;
import com.smartjourney.server.journey.domain.JourneyStop;
import com.smartjourney.server.journey.domain.StopStatus;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.waiting.api.WaitingDtos.WaitingResponse;
import com.smartjourney.server.waiting.application.WaitingService;
import com.smartjourney.server.waiting.application.WaitingViews;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여정(코스) — 기능 1.1 인원 · 1.2 코스 · 1.3 인원 변동 · 5.1 음성 브리핑 재료.
 *
 * <ul>
 *   <li>create: 기기당 ACTIVE 여정 1개 (기기 행 잠금 → 있으면 409 ACTIVE_JOURNEY_EXISTS). 최대 3곳, 중복 매장 금지</li>
 *   <li>current: 앱 프로세스 복구 · Gleo CHECK_WAITING_STATUS 재료 — 없으면 204</li>
 *   <li>changeParty: 여정 인원만 바꾼다. 접수된 웨이팅 인원은 PATCH /waitings/{id}/party-size 로 따로 (정책 2)</li>
 *   <li>end: 줄 서 있는(REGISTERED·ARRIVED) 웨이팅은 취소, 웨이팅 전 매장은 SKIPPED. 호출된 팀은 매장에 있으니 그대로</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JourneyService {

    /** KPI attributes 에 들어오면 안 되는 키 (정책 4: 좌표·궤적·VIN·음성 원문) */
    private static final Set<String> FORBIDDEN_ATTRIBUTE_KEYS = Set.of(
            "lat", "lng", "lon", "latitude", "longitude", "location", "coord", "coords", "coordinate",
            "coordinates", "position", "trajectory", "route", "vin", "utterance", "transcript", "speech", "voicetext");

    private final JourneyRepository journeyRepository;
    private final JourneyEventRepository journeyEventRepository;
    private final DeviceRepository deviceRepository;
    private final StoreRepository storeRepository;
    private final WaitingRepository waitingRepository;
    private final WaitingService waitingService;
    private final WaitingViews waitingViews;
    private final JsonCodec json;
    private final Clock clock;

    @Transactional
    public JourneyResponse create(long deviceId, CreateJourneyRequest request) {
        List<Long> storeIds = request.storeIds();
        validateStops(storeIds);
        deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (journeyRepository.findFirstByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, JourneyStatus.ACTIVE)
                .isPresent()) {
            throw new BusinessException(ErrorCode.ACTIVE_JOURNEY_EXISTS);
        }
        Journey journey = journeyRepository.save(Journey.start(deviceId, request.partySize(), request.partySource(),
                storeIds, clock.instant()));
        return toResponse(journey);
    }

    @Transactional(readOnly = true)
    public Optional<JourneyResponse> current(long deviceId) {
        return journeyRepository.findFirstByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, JourneyStatus.ACTIVE)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public JourneyResponse get(long deviceId, long journeyId) {
        return toResponse(owned(deviceId, journeyId));
    }

    @Transactional
    public JourneyResponse changeParty(long deviceId, long journeyId, ChangePartyRequest request) {
        Journey journey = active(deviceId, journeyId);
        journey.changeParty(request.partySize(), request.partySource());
        return toResponse(journey);
    }

    /**
     * 코스 순서 변경 (P2-1, 승인 후에만). 기존 매장들의 새 순서만 받는다 — 매장 추가·삭제는 안 됨.
     * 앱은 응답을 받은 뒤 requestRouteWithWaypoints 를 다시 부른다.
     */
    @Transactional
    public JourneyResponse reorderStops(long deviceId, long journeyId, ReorderStopsRequest request) {
        Journey journey = active(deviceId, journeyId);
        List<Long> newOrder = request.storeIds();
        Set<Long> current = journey.getStops().stream().map(JourneyStop::getStoreId).collect(Collectors.toSet());
        if (newOrder.size() != current.size() || !current.equals(new HashSet<>(newOrder))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "기존 코스 매장들의 순서만 바꿀 수 있습니다.");
        }
        journey.shiftSeqs(Journey.MAX_STOPS * 10);
        journeyRepository.saveAndFlush(journey);         // (journey_id, seq) 유니크 충돌 피하기
        journey.applyOrder(newOrder);
        return toResponse(journey);
    }

    @Transactional
    public JourneyResponse end(long deviceId, long journeyId) {
        Journey journey = owned(deviceId, journeyId);
        if (!journey.isActive()) {
            return toResponse(journey);                    // 두 번 눌러도 같은 결과
        }
        endJourney(journey, "JOURNEY_END");
        return toResponse(journey);
    }

    /**
     * 동의 철회 (정책 4 · 3-6). 철회하면 차량은 더 이상 아무것도 보낼 수 없으므로
     * 진행 중 여정을 끝내고 줄 서 있는 웨이팅은 취소해 매장이 기다리지 않게 한다. 호출된 팀은 매장 판단에 맡긴다.
     */
    @Transactional
    public void endAllForRevokedDevice(long deviceId) {
        journeyRepository.findFirstByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, JourneyStatus.ACTIVE)
                .ifPresent(j -> endJourney(j, "CONSENT_REVOKED"));
        for (Waiting w : waitingRepository.findByDeviceIdAndStatusIn(deviceId, WaitingStatus.COUNTED_AHEAD)) {
            waitingService.cancelBySystem(w, "CONSENT_REVOKED");
        }
    }

    private void endJourney(Journey journey, String reason) {
        for (Waiting w : waitingRepository.findByJourneyIdAndStatusIn(journey.getId(), WaitingStatus.COUNTED_AHEAD)) {
            waitingService.cancelBySystem(w, reason);
        }
        journey.getStops().stream()
                .filter(s -> s.getStatus() == StopStatus.PLANNED)
                .forEach(JourneyStop::skip);
        journey.end(clock.instant());
    }

    /** 차량 안에서만 일어나는 일 (KPI). 좌표·음성 원문 키가 있으면 거절 */
    @Transactional
    public void recordEvent(long deviceId, long journeyId, JourneyEventRequest request) {
        Journey journey = owned(deviceId, journeyId);
        Map<String, Object> attributes = request.attributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                if (FORBIDDEN_ATTRIBUTE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "위치·음성 원문 같은 값은 보낼 수 없습니다: " + key);
                }
            }
        }
        String payload = attributes == null || attributes.isEmpty() ? null : json.write(attributes);
        if (payload != null && payload.length() > 4000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "attributes 가 너무 큽니다.");
        }
        journeyEventRepository.save(JourneyEvent.of(journey.getId(), request.storeId(), request.type(), payload,
                request.occurredAt() != null ? request.occurredAt() : clock.instant()));
    }

    // ------------------------------------------------------------------

    private void validateStops(List<Long> storeIds) {
        if (storeIds.size() > Journey.MAX_STOPS) {
            throw new BusinessException(ErrorCode.TOO_MANY_STOPS);
        }
        if (new HashSet<>(storeIds).size() != storeIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 매장이 두 번 들어 있습니다.");
        }
        if (storeRepository.findAllById(storeIds).size() != storeIds.size()) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }
    }

    private Journey owned(long deviceId, long journeyId) {
        return journeyRepository.findByIdAndDeviceId(journeyId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOURNEY_NOT_FOUND));
    }

    private Journey active(long deviceId, long journeyId) {
        Journey journey = owned(deviceId, journeyId);
        if (!journey.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 끝난 여정입니다.");
        }
        return journey;
    }

    private JourneyResponse toResponse(Journey journey) {
        Map<Long, Store> stores = storeRepository.findAllById(
                        journey.getStops().stream().map(JourneyStop::getStoreId).toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        List<StopView> stops = journey.getStops().stream()
                .sorted(Comparator.comparingInt(JourneyStop::getSeq))
                .map(stop -> {
                    Store store = stores.get(stop.getStoreId());
                    return new StopView(stop.getSeq(), stop.getStoreId(), store != null ? store.getName() : null,
                            stop.getStatus(), brief(stop, store));
                })
                .toList();
        return new JourneyResponse(journey.getId(), journey.getStatus(), journey.getPartySize(),
                journey.getPartySource(), journey.getStartedAt(), stops);
    }

    private WaitingBrief brief(JourneyStop stop, Store store) {
        if (stop.getWaitingId() == null || store == null) {
            return null;
        }
        return waitingRepository.findById(stop.getWaitingId())
                .map(w -> {
                    WaitingResponse r = waitingViews.toResponse(w, store);
                    return new WaitingBrief(r.waitingId(), r.ticketNo(), r.status(), r.partySize(), r.aheadTeams(),
                            r.estimatedEntryAt());
                })
                .orElse(null);
    }
}
