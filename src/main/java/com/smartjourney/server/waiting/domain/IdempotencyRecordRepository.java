package com.smartjourney.server.waiting.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByDeviceIdAndIdemKey(Long deviceId, String idemKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.deviceId = :deviceId")
    int deleteByDevice(@Param("deviceId") Long deviceId);
}
