package com.smartjourney.server.waiting.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.waiting.api.WaitingDtos.ChangePartySizeRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.CreateWaitingRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.DeferRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.EtaUpdateRequest;
import com.smartjourney.server.waiting.api.WaitingDtos.TicketResponse;
import com.smartjourney.server.waiting.api.WaitingDtos.WaitingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 차량 앱 쪽 웨이팅 명령·조회.
 *
 * <p>구현 메모 (Phase 1~2):
 * <ol>
 *   <li>create (P0-3): Idempotency-Key 조회 → 있으면 저장 응답 재사용 / 요청 해시 다르면 409.
 *       여정 소유·ACTIVE 확인 → 같은 여정·매장 진행 중 웨이팅 있으면 409 DUPLICATE_ACTIVE_WAITING →
 *       StoreRepository.findByIdForUpdate 로 매장 잠금 → 오늘(Asia/Seoul) 번호표 max+1 →
 *       Waiting.fromVehicle 저장 → JourneyStop.linkWaiting → WaitingEvent(REGISTERED) →
 *       RealtimeMessage: /topic/pos/{storeId} WAITING_CREATED, /topic/stores/{storeId}/queue QUEUE_UPDATED.
 *       목표: 승인 후 2초 안에 POS 보드에 등장 (인수 조건 P0-3). Gleo 콜백 7초 제한 → p95 1초 이내</li>
 *   <li>changePartySize (P1-1): canChangePartySize 아니면 409 WAITING_ALREADY_CALLED → PARTY_SIZE_CHANGED 이벤트</li>
 *   <li>defer (P2-1): DeferPolicy.canDefer / teamsToPushBack / newQueueOrder → 매장 잠금 → QUEUE_UPDATED (뒤 팀들 순번도 변함)</li>
 *   <li>updateEta (P0-5 보조): Waiting.updateEta → /topic/pos ETA_UPDATED</li>
 *   <li>ticket (Phase 2): CALLED 일 때만 QrTicketCodec.encode</li>
 *   <li>cancel: REGISTERED/ARRIVED → CANCELLED → JourneyStop.markDone → QUEUE_UPDATED</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class WaitingService {

    public WaitingResponse create(long deviceId, String idempotencyKey, CreateWaitingRequest request) {
        throw BusinessException.notImplemented("Phase 1 · P0-3 승인 접수");
    }

    public WaitingResponse get(long deviceId, long waitingId) {
        throw BusinessException.notImplemented("Phase 1 · 웨이팅 조회");
    }

    public WaitingResponse changePartySize(long deviceId, long waitingId, ChangePartySizeRequest request) {
        throw BusinessException.notImplemented("Phase 3 · P1-1 인원 변동 반영");
    }

    public WaitingResponse defer(long deviceId, long waitingId, DeferRequest request) {
        throw BusinessException.notImplemented("Phase 4 · P2-1 순번 미루기");
    }

    public WaitingResponse updateEta(long deviceId, long waitingId, EtaUpdateRequest request) {
        throw BusinessException.notImplemented("Phase 2 · ETA 전달");
    }

    public TicketResponse ticket(long deviceId, long waitingId) {
        throw BusinessException.notImplemented("Phase 2 · 번호표 QR");
    }

    public WaitingResponse cancel(long deviceId, long waitingId) {
        throw BusinessException.notImplemented("Phase 1 · 웨이팅 취소");
    }
}
