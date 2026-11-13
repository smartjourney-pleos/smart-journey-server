package com.smartjourney.server.arrival.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArrivalCheckRepository extends JpaRepository<ArrivalCheck, Long> {

    Optional<ArrivalCheck> findFirstByWaitingIdOrderByCreatedAtDescIdDesc(Long waitingId);

    /** POS 보드: 여러 팀의 하차 배지를 한 번에 (마지막 보고가 유효) */
    List<ArrivalCheck> findByWaitingIdInOrderByCreatedAtAscIdAsc(Collection<Long> waitingIds);

    List<ArrivalCheck> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    @Modifying
    @Query("delete from ArrivalCheck a where a.waitingId in :waitingIds")
    int deleteByWaitingIds(@Param("waitingIds") Collection<Long> waitingIds);
}
