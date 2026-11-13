package com.smartjourney.server.waiting.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.config.ClockConfig;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.realtime.RealtimeEventType;
import com.smartjourney.server.common.util.JsonCodec;
import com.smartjourney.server.journey.application.JourneyProgress;
import com.smartjourney.server.journey.domain.Journey;
import com.smartjourney.server.journey.domain.JourneyRepository;
import com.smartjourney.server.journey.domain.JourneyStop;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import com.smartjourney.server.ticket.domain.QrTicketCodec;
import com.smartjourney.server.ticket.domain.QrTicketCodec.QrTicket;
import com.smartjourney.server.waiting.api.WaitingDtos.ChangePartySizeRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.CreateWaitingRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.DeferRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.EtaUpdateRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.TicketResponse;
import com.smartjourney.server.waiting.api.WaitingDtos.WaitingResponse;
import com.smartjourney.server.waiting.domain.DeferPolicy;
import com.smartjourney.server.waiting.domain.IdempotencyRecord;
import com.smartjourney.server.waiting.domain.IdempotencyRecordRepository;
import com.smartjourney.server.waiting.domain.Waiting;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingRepository;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량 앱 쪽 웨이팅 명령·조회.
 *
 * <p>접수(P0-3) 흐름: Idempotency-Key 조회 → 여정 소유·ACTIVE 확인 → 매장 행 잠금 → (잠금 뒤) 키 재확인 →
 * 중복 웨이팅 확인 → 오늘(Asia/Seoul) 번호표 max+1 → 저장 → 정차 연결 → 감사 로그 → 커밋 후 STOMP.
 * 목표: 승인 후 2초 안에 POS 보드에 등장. Gleo 콜백 7초 제한 → p95 1초.
 *
 * <p>멱등 해시에 approvalMethod 는 넣지 않는다: 화면 승인과 음성 승인이 같은 키로 거의 동시에 와도 하나만 생겨야 한다.
 * 같은 키 재요청은 같은 웨이팅의 <b>현재</b> 상태를 돌려준다 (번호표·waitingId 동일).
 */
@Service
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingRepository waitingRepository;
    private final StoreRepository storeRepository;
    private final JourneyRepository journeyRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final WaitingViews views;
    private final WaitingHistory history;
    private final QueueBroadcaster broadcaster;
    private final JourneyProgress journeyProgress;
    private final QrTicketCodec qrTicketCodec;
    private final AppProperties properties;
    private final JsonCodec json;
    private final Clock clock;

    // ------------------------------------------------------------------ 접수

    @Transactional
    public WaitingResponse create(long deviceId, String idempotencyKey, CreateWaitingRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String requestHash = hash(request.journeyId() + ":" + request.storeId() + ":" + request.partySize());

        Optional<WaitingResponse> replay = replay(deviceId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Journey journey = journeyRepository.findByIdAndDeviceId(request.journeyId(), deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOURNEY_NOT_FOUND));
        if (!journey.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 끝난 여정입니다.");
        }
        JourneyStop stop = journey.stopForStore(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "이 여정의 코스에 없는 매장입니다."));

        Store store = storeRepository.findByIdForUpdate(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        // 매장 잠금을 기다리는 동안 같은 키 요청이 먼저 끝났을 수 있다
        replay = replay(deviceId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (waitingRepository.existsByJourneyIdAndStoreIdAndStatusIn(journey.getId(), store.getId(),
                WaitingStatus.ACTIVE) || !stop.canRegisterWaiting()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_WAITING);
        }

        Instant now = clock.instant();
        LocalDate businessDate = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);
        int ticketNo = waitingRepository.findMaxTicketNo(store.getId(), businessDate) + 1;
        Waiting waiting = waitingRepository.saveAndFlush(Waiting.fromVehicle(
                store.getId(), journey.getId(), deviceId, businessDate, ticketNo, request.partySize(),
                request.approvalMethod(), now));
        stop.linkWaiting(waiting.getId());
        history.record(waiting, WaitingEventType.REGISTERED,
                Map.of("partySize", waiting.getPartySize(), "approvalMethod", request.approvalMethod()));

        WaitingResponse response = views.toResponse(waiting, store);
        idempotencyRepository.save(IdempotencyRecord.of(deviceId, idempotencyKey, requestHash,
                HttpStatus.CREATED.value(), json.write(response), now));

        broadcaster.toPos(RealtimeEventType.WAITING_CREATED, waiting);
        broadcaster.queueChanged(store);
        return response;
    }

    private Optional<WaitingResponse> replay(long deviceId, String key, String requestHash) {
        return idempotencyRepository.findByDeviceIdAndIdemKey(deviceId, key).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            long waitingId = json.read(record.getResponseBody(), WaitingResponse.class).waitingId();
            return get(deviceId, waitingId);
        });
    }

    // ------------------------------------------------------------------ 조회

    @Transactional(readOnly = true)
    public WaitingResponse get(long deviceId, long waitingId) {
        Waiting waiting = owned(deviceId, waitingId);
        return views.toResponse(waiting, store(waiting.getStoreId()));
    }

    // ------------------------------------------------------------------ 인원 변경 (P1-1)

    @Transactional
    public WaitingResponse changePartySize(long deviceId, long waitingId, ChangePartySizeRequest request) {
        Waiting waiting = owned(deviceId, waitingId);
        if (waiting.getStatus() == WaitingStatus.CALLED) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_CALLED);
        }
        if (!waiting.getStatus().canChangePartySize()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        int from = waiting.getPartySize();
        if (from != request.partySize()) {
            waiting.changePartySize(request.partySize());
            history.record(waiting, WaitingEventType.PARTY_SIZE_CHANGED, Map.of("from", from, "to", request.partySize()));
            broadcaster.toPos(RealtimeEventType.PARTY_SIZE_CHANGED, waiting);
        }
        Store store = store(waiting.getStoreId());
        broadcaster.waitingUpdated(waiting, store);
        return views.toResponse(waiting, store);
    }

    // ------------------------------------------------------------------ 순번 미루기 (P2-1)

    @Transactional
    public WaitingResponse defer(long deviceId, long waitingId, DeferRequest request) {
        Waiting waiting = owned(deviceId, waitingId);
        // 순번은 매장 단위로 바뀌므로 매장 행을 잠그고 계산한다 (웨이팅 자체 경합은 @Version 이 409 로)
        Store store = storeRepository.findByIdForUpdate(waiting.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        if (!waiting.getStatus().canDefer()) {
            throw new BusinessException(waiting.getStatus() == WaitingStatus.CALLED
                    ? ErrorCode.WAITING_ALREADY_CALLED : ErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!DeferPolicy.canDefer(waiting.getDeferCount(), store.getMaxDefers())) {
            throw new BusinessException(ErrorCode.DEFER_LIMIT_EXCEEDED);
        }

        List<Long> ordersBehind = waitingRepository
                .findByStoreIdAndStatusInAndQueueOrderGreaterThanOrderByQueueOrderAsc(
                        store.getId(), WaitingStatus.COUNTED_AHEAD, waiting.getQueueOrder())
                .stream().map(Waiting::getQueueOrder).toList();
        int teams = DeferPolicy.teamsToPushBack(request.delayMin(), store.getMinutesPerTeam());
        long newOrder = DeferPolicy.newQueueOrder(waiting.getQueueOrder(), ordersBehind, teams);

        if (newOrder != waiting.getQueueOrder()) {     // 뒤에 아무도 없으면 횟수를 쓰지 않는다
            waiting.deferTo(newOrder);
            history.record(waiting, WaitingEventType.DEFERRED,
                    Map.of("delayMin", request.delayMin(), "teams", Math.min(teams, ordersBehind.size())));
            broadcaster.queueChanged(store);
        }
        return views.toResponse(waiting, store);
    }

    // ------------------------------------------------------------------ ETA (P0-5 보조)

    @Transactional
    public WaitingResponse updateEta(long deviceId, long waitingId, EtaUpdateRequest request) {
        Waiting waiting = owned(deviceId, waitingId);
        if (waiting.getStatus().isClosed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        Integer before = waiting.getEtaMin();
        waiting.updateEta(request.etaMin(), clock.instant());
        if (before == null || !before.equals(waiting.getEtaMin())) {
            history.record(waiting, WaitingEventType.ETA_UPDATED, Map.of("etaMin", waiting.getEtaMin()));
        }
        broadcaster.toPos(RealtimeEventType.ETA_UPDATED, waiting);
        return views.toResponse(waiting, store(waiting.getStoreId()));
    }

    // ------------------------------------------------------------------ 번호표 QR (V-11)

    @Transactional(readOnly = true)
    public TicketResponse ticket(long deviceId, long waitingId) {
        Waiting waiting = owned(deviceId, waitingId);
        if (waiting.getStatus() != WaitingStatus.CALLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "호출된 뒤에 번호표 QR 이 나옵니다.");
        }
        return issueTicket(waiting, store(waiting.getStoreId()), clock.instant());
    }

    /** QR TTL 은 발급 시점부터 (호출 시 1번 + 앱이 다시 열 때마다 새로) */
    public TicketResponse issueTicket(Waiting waiting, Store store, Instant now) {
        Instant expiresAt = now.plus(properties.qr().ttl());
        String payload = qrTicketCodec.encode(
                new QrTicket(waiting.getId(), waiting.getStoreId(), waiting.getTicketNo(), expiresAt));
        return new TicketResponse(waiting.getId(), store.getName(), waiting.getTicketNo(), waiting.getPartySize(),
                payload, expiresAt);
    }

    // ------------------------------------------------------------------ 취소

    @Transactional
    public WaitingResponse cancel(long deviceId, long waitingId) {
        Waiting waiting = owned(deviceId, waitingId);
        Store store = storeRepository.findByIdForUpdate(waiting.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (waiting.getStatus() == WaitingStatus.CALLED) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_CALLED, "호출된 뒤에는 매장에 직접 말씀해 주세요.");
        }
        transition(waiting, WaitingStatus.CANCELLED);
        closeAndNotify(waiting, store, WaitingEventType.CANCELLED, Map.of("by", "VEHICLE"));
        return views.toResponse(waiting, store);
    }

    /**
     * 시스템이 대신 취소 (여정 끝내기·동의 철회). REGISTERED·ARRIVED 만 — 호출된 팀은 매장에 있으니 그대로 둔다.
     * 호출하는 쪽이 같은 트랜잭션 안에 있어야 한다.
     */
    public boolean cancelBySystem(Waiting waiting, String reason) {
        if (!waiting.getStatus().isInLine()) {
            return false;
        }
        Store store = storeRepository.findByIdForUpdate(waiting.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        transition(waiting, WaitingStatus.CANCELLED);
        closeAndNotify(waiting, store, WaitingEventType.CANCELLED, Map.of("by", reason));
        return true;
    }

    private void closeAndNotify(Waiting waiting, Store store, WaitingEventType type, Map<String, ?> payload) {
        history.record(waiting, type, payload);
        journeyProgress.waitingClosed(waiting);
        broadcaster.toPos(RealtimeEventType.WAITING_CANCELLED, waiting);
        broadcaster.waitingUpdated(waiting, store);
        broadcaster.queueChanged(store);
    }

    // ------------------------------------------------------------------ 공통

    private void transition(Waiting waiting, WaitingStatus next) {
        try {
            waiting.transitionTo(next, clock.instant());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private Waiting owned(long deviceId, long waitingId) {
        return waitingRepository.findByIdAndDeviceId(waitingId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
    }

    private Store store(long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
