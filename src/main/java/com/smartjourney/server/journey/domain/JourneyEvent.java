package com.smartjourney.server.journey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "journey_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JourneyEventType type;

    @Column(length = 4000)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static JourneyEvent of(long journeyId, Long storeId, JourneyEventType type, String payload,
                                  Instant occurredAt) {
        JourneyEvent e = new JourneyEvent();
        e.journeyId = journeyId;
        e.storeId = storeId;
        e.type = type;
        e.payload = payload;
        e.occurredAt = occurredAt;
        return e;
    }
}
