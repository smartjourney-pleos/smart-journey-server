package com.smartjourney.server.journey.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    /** 앱 프로세스가 죽었다 살아났을 때 진행 중 여정 복구 */
    Optional<Journey> findFirstByDeviceIdAndStatusOrderByStartedAtDesc(Long deviceId, JourneyStatus status);

    Optional<Journey> findByIdAndDeviceId(Long id, Long deviceId);

    List<Journey> findByStartedAtGreaterThanEqualAndStartedAtLessThan(Instant from, Instant to);

    /** 보유 기간 파기: 끝난 지 오래된 여정 */
    @Query("select j.id from Journey j where j.status = :status and j.endedAt < :before")
    List<Long> findEndedIdsBefore(@Param("status") JourneyStatus status, @Param("before") Instant before);

    @Query("select j.id from Journey j where j.deviceId = :deviceId")
    List<Long> findIdsByDeviceId(@Param("deviceId") Long deviceId);

    boolean existsByDeviceId(Long deviceId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from JourneyStop s where s.journey.id in :journeyIds")
    int deleteStopsByJourneyIds(@Param("journeyIds") java.util.Collection<Long> journeyIds);
}
