package com.smartjourney.server.journey.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyEventRepository extends JpaRepository<JourneyEvent, Long> {
}
