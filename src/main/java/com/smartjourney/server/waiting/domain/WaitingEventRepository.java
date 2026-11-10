package com.smartjourney.server.waiting.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitingEventRepository extends JpaRepository<WaitingEvent, Long> {

    List<WaitingEvent> findByWaitingIdOrderByOccurredAtAsc(Long waitingId);
}
