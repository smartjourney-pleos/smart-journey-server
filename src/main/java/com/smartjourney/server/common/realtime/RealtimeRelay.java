package com.smartjourney.server.common.realtime;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션이 커밋된 뒤에만 STOMP 로 내보낸다.
 * 트랜잭션 밖에서 발행된 메시지는 바로 보낸다 (fallbackExecution).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRelay {

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(RealtimeMessage message) {
        EventEnvelope envelope = new EventEnvelope(
                message.type(), UUID.randomUUID().toString(), clock.instant(), message.payload());
        log.debug("STOMP {} -> {}", message.type(), message.destination());
        messagingTemplate.convertAndSend(message.destination(), envelope);
    }
}
