package com.smartjourney.server.waiting.domain;

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
@Table(name = "waiting_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waiting_id", nullable = false)
    private Long waitingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitingEventType type;

    /** 작은 JSON 문자열 (예: {"from":4,"to":3}). 좌표 등 개인정보는 넣지 않는다 */
    @Column(length = 4000)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static WaitingEvent of(long waitingId, WaitingEventType type, String payload, Instant occurredAt) {
        WaitingEvent e = new WaitingEvent();
        e.waitingId = waitingId;
        e.type = type;
        e.payload = payload;
        e.occurredAt = occurredAt;
        return e;
    }
}
