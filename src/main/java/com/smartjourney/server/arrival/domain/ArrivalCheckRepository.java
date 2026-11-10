package com.smartjourney.server.arrival.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArrivalCheckRepository extends JpaRepository<ArrivalCheck, Long> {

    Optional<ArrivalCheck> findFirstByWaitingIdOrderByCreatedAtDesc(Long waitingId);
}
