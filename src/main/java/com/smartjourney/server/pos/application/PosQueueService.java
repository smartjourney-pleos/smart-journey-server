package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.pos.api.PosDtos.PosQueueResponse;
import com.smartjourney.server.pos.api.PosDtos.PosWaitingDetail;
import com.smartjourney.server.pos.api.PosDtos.VerifyTicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * POS 대기열 보드·호출·착석·노쇼 (P-02 ~ P-04, 기능 6.1).
 *
 * <p>구현 메모:
 * <ul>
 *   <li>queue (Phase 1): ACTIVE 상태 queue_order 순 + 최신 ArrivalCheck 배지</li>
 *   <li>call (Phase 2): REGISTERED/ARRIVED → CALLED. /topic/waitings/{id} CALLED {qrPayload} (QrTicketCodec, TTL 30분)</li>
 *   <li>seat / noShow (Phase 2): CALLED → SEATED / NO_SHOW → JourneyStop.markDone → QUEUE_UPDATED</li>
 *   <li>모든 명령은 storeId 가 토큰의 매장과 같은 웨이팅만 (다르면 404 로 숨김)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PosQueueService {

    public PosQueueResponse queue(long storeId) {
        throw BusinessException.notImplemented("Phase 1 · P0-4 POS 대기열 보드");
    }

    public PosWaitingDetail detail(long storeId, long waitingId) {
        throw BusinessException.notImplemented("Phase 2 · P-03 팀 상세");
    }

    public PosWaitingDetail call(long storeId, long waitingId) {
        throw BusinessException.notImplemented("Phase 2 · 호출 + QR");
    }

    public PosWaitingDetail seat(long storeId, long waitingId) {
        throw BusinessException.notImplemented("Phase 2 · 착석");
    }

    public PosWaitingDetail noShow(long storeId, long waitingId) {
        throw BusinessException.notImplemented("Phase 2 · 노쇼");
    }

    public VerifyTicketResponse verifyTicket(long storeId, String qrPayload) {
        throw BusinessException.notImplemented("Phase 4 · 입장 QR 확인");
    }
}
