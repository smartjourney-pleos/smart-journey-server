package com.smartjourney.server.waiting.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitingEventRepository extends JpaRepository<WaitingEvent, Long> {

    List<WaitingEvent> findByWaitingIdOrderByOccurredAtAscIdAsc(Long waitingId);

    @Modifying
    @Query("delete from WaitingEvent e where e.waitingId in :waitingIds")
    int deleteByWaitingIds(@Param("waitingIds") Collection<Long> waitingIds);
}
