package com.smartjourney.server.device.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceToken(String deviceToken);

    /** 여정 생성처럼 "기기당 하나" 규칙을 지켜야 하는 작업은 기기 행을 잠그고 한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") Long id);

    /** 보유 기간 파기: 철회한 지 오래된 기기 */
    @Query("select d.id from Device d where d.revokedAt is not null and d.revokedAt < :before")
    List<Long> findRevokedIdsBefore(@Param("before") Instant before);
}
