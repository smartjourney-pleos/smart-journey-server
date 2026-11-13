package com.smartjourney.server.waiting.platform;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 웨이팅 변화를 등록된 외부 플랫폼 어댑터에 넘긴다. 어댑터가 없으면 아무 일도 안 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalWaitingPlatformBridge {

    private final List<ExternalWaitingPlatform> platforms;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLifecycle(WaitingLifecycleEvent event) {
        for (ExternalWaitingPlatform platform : platforms) {
            try {
                platform.publish(event);
            } catch (RuntimeException e) {
                // 외부 실패가 우리 흐름을 막지 않게 — 기록만
                log.warn("[platform:{}] publish failed: waiting={} type={} ({})",
                        platform.name(), event.waitingId(), event.type(), e.getMessage());
            }
        }
    }
}
