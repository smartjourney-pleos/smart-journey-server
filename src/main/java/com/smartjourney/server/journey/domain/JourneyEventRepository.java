package com.smartjourney.server.journey.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyEventRepository extends JpaRepository<JourneyEvent, Long> {

    List<JourneyEvent> findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant from, Instant to);

    @Modifying
    @Query("delete from JourneyEvent e where e.journeyId in :journeyIds")
    int deleteByJourneyIds(@Param("journeyIds") Collection<Long> journeyIds);
}
