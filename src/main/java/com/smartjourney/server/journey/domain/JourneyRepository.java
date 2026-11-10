package com.smartjourney.server.journey.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    /** 앱 프로세스가 죽었다 살아났을 때 진행 중 여정 복구 */
    Optional<Journey> findFirstByDeviceIdAndStatusOrderByStartedAtDesc(Long deviceId, JourneyStatus status);

    Optional<Journey> findByIdAndDeviceId(Long id, Long deviceId);
}
