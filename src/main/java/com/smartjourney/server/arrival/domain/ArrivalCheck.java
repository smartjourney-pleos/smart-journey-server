package com.smartjourney.server.arrival.domain;

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

/**
 * 도착·하차 확인 1건 + 근거 데이터 (좌표 없음 — 시각과 점유 수만).
 * 인원 수 확인이며 신원 인증이 아니다.
 */
@Getter
@Entity
@Table(name = "arrival_check")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArrivalCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waiting_id", nullable = false)
    private Long waitingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ArrivalResult result;

    /** M. UNCONFIRMED 면 null */
    private Integer alighted;

    /** N */
    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "door_opened_at")
    private Instant doorOpenedAt;

    /** Door.registerDoorLockState — 전부 잠김 = 운전자도 내린 것으로 보는 보조 신호 (선택) */
    @Column(name = "door_locked_at")
    private Instant doorLockedAt;

    /** Seat.registerSeatBeltBuckledState — 벨트 풀림 → 점유 해제 순서 보조 신호 (선택) */
    @Column(name = "belt_released_at")
    private Instant beltReleasedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "occupancy_before")
    private Integer occupancyBefore;

    @Column(name = "occupancy_after")
    private Integer occupancyAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ArrivalCheck of(long waitingId, ArrivalResult result, Integer alighted, int partySize,
                                  ArrivalEvidence evidence, Instant now) {
        ArrivalCheck c = new ArrivalCheck();
        c.waitingId = waitingId;
        c.result = result;
        c.alighted = alighted;
        c.partySize = partySize;
        c.arrivedAt = evidence.arrivedAt() != null ? evidence.arrivedAt() : now;
        c.doorOpenedAt = evidence.doorOpenedAt();
        c.doorLockedAt = evidence.doorLockedAt();
        c.beltReleasedAt = evidence.beltReleasedAt();
        c.settledAt = evidence.settledAt();
        c.occupancyBefore = evidence.occupancyBefore();
        c.occupancyAfter = evidence.occupancyAfter();
        c.createdAt = now;
        return c;
    }
}
