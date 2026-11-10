package com.smartjourney.server.journey.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.journey.api.JourneyDtos.ChangePartyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.CreateJourneyRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyEventRequest;
import com.smartjourney.server.journey.api.JourneyDtos.JourneyResponse;
import com.smartjourney.server.journey.api.JourneyDtos.ReorderStopsRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 여정(코스) — 기능 1.1 인원 · 1.2 코스 · 1.3 인원 변동 · 5.1 음성 브리핑 재료.
 *
 * <p>Phase 1 에서 구현. 구현 메모:
 * <ul>
 *   <li>create: 기기당 ACTIVE 여정 1개 (있으면 409 ACTIVE_JOURNEY_EXISTS). 매장 존재·중복 검사. 최대 3곳</li>
 *   <li>current: 앱 프로세스 복구용 — 없으면 204</li>
 *   <li>changeParty: 여정 인원만 바꾼다. 이미 접수된 웨이팅 인원은 PATCH /waitings/{id}/party-size 로 따로 (정책 2: 승인 후에만)</li>
 *   <li>end: 진행 중 웨이팅이 있으면 취소할지 클라이언트가 먼저 묻는다 — 서버는 남은 REGISTERED 를 CANCELLED 로</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JourneyService {

    public JourneyResponse create(long deviceId, CreateJourneyRequest request) {
        throw BusinessException.notImplemented("Phase 1 · P0-1/P0-3 여정 생성");
    }

    public Optional<JourneyResponse> current(long deviceId) {
        throw BusinessException.notImplemented("Phase 1 · P0-8 진행 중 여정 복구");
    }

    public JourneyResponse get(long deviceId, long journeyId) {
        throw BusinessException.notImplemented("Phase 1 · V-06 진행 현황");
    }

    public JourneyResponse changeParty(long deviceId, long journeyId, ChangePartyRequest request) {
        throw BusinessException.notImplemented("Phase 1 · P0-1 인원 수정(MANUAL)");
    }

    public JourneyResponse reorderStops(long deviceId, long journeyId, ReorderStopsRequest request) {
        throw BusinessException.notImplemented("Phase 4 · P2-1 경유지 순서 변경");
    }

    public JourneyResponse end(long deviceId, long journeyId) {
        throw BusinessException.notImplemented("Phase 1 · 여정 종료");
    }

    public void recordEvent(long deviceId, long journeyId, JourneyEventRequest request) {
        throw BusinessException.notImplemented("Phase 3 · KPI 이벤트");
    }
}
