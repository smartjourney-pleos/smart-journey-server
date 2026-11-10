package com.smartjourney.server.waiting.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByDeviceIdAndIdemKey(Long deviceId, String idemKey);
}
