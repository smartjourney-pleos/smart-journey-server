package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.pos.api.PosDtos.DemoResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 데모 패널 (P-06). 2차 PT 핵심 장면: 심사위원이 현장 대기 팀을 늘리면 차량 상태 알림 숫자가 바로 바뀐다 (T2).
 *
 * <p>구현 메모 (Phase 1):
 * <ul>
 *   <li>addWalkIns: 매장 잠금 → Waiting.walkIn × count → QUEUE_UPDATED 1번만 발행 (여러 번 X)</li>
 *   <li>removeWalkIns: 가장 늦게 들어온 WALK_IN 부터 CANCELLED</li>
 *   <li>reset: 오늘 영업일 웨이팅 전부 CANCELLED (리허설 반복용)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PosDemoService {

    private final AppProperties properties;

    public DemoResult addWalkIns(long storeId, int count, int partySize) {
        requireDemoEnabled();
        throw BusinessException.notImplemented("Phase 1 · P0-4 데모 패널 +N");
    }

    public DemoResult removeWalkIns(long storeId, int count) {
        requireDemoEnabled();
        throw BusinessException.notImplemented("Phase 1 · P0-4 데모 패널 −N");
    }

    public DemoResult reset(long storeId) {
        requireDemoEnabled();
        throw BusinessException.notImplemented("Phase 4 · 리허설 초기화");
    }

    private void requireDemoEnabled() {
        if (!properties.demo().enabled()) {
            throw new BusinessException(ErrorCode.DEMO_DISABLED);
        }
    }
}
