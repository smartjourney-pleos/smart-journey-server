package com.smartjourney.server.waiting.domain;

import com.smartjourney.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기열의 한 팀. 다른 애그리거트(매장·여정·기기)는 id 로만 참조한다.
 */
@Getter
@Entity
@Table(name = "waiting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiting extends BaseTimeEntity {

    public static final int MIN_PARTY = 1;
    public static final int MAX_PARTY = 9;   // 좌석 영역 최대 9 (SeatValue ROW_1~3 × LEFT/CENTER/RIGHT)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "journey_id")
    private Long journeyId;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WaitingChannel channel;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "ticket_no", nullable = false)
    private int ticketNo;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WaitingStatus status;

    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    @Column(name = "defer_count", nullable = false)
    private int deferCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_method", length = 10)
    private ApprovalMethod approvalMethod;

    @Column(name = "eta_min")
    private Integer etaMin;

    @Column(name = "eta_updated_at")
    private Instant etaUpdatedAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    public static Waiting fromVehicle(long storeId, long journeyId, long deviceId, LocalDate businessDate,
                                      int ticketNo, int partySize, ApprovalMethod approvalMethod, Instant now) {
        Waiting w = base(storeId, WaitingChannel.VEHICLE, businessDate, ticketNo, partySize, now);
        w.journeyId = journeyId;
        w.deviceId = deviceId;
        w.approvalMethod = approvalMethod;
        return w;
    }

    public static Waiting walkIn(long storeId, LocalDate businessDate, int ticketNo, int partySize, Instant now) {
        return base(storeId, WaitingChannel.WALK_IN, businessDate, ticketNo, partySize, now);
    }

    private static Waiting base(long storeId, WaitingChannel channel, LocalDate businessDate,
                                int ticketNo, int partySize, Instant now) {
        requireValidPartySize(partySize);
        Waiting w = new Waiting();
        w.storeId = storeId;
        w.channel = channel;
        w.businessDate = businessDate;
        w.ticketNo = ticketNo;
        w.partySize = partySize;
        w.status = WaitingStatus.REGISTERED;
        w.queueOrder = QueueCalculator.initialQueueOrder(now);
        w.registeredAt = now;
        return w;
    }

    public static boolean isValidPartySize(int partySize) {
        return partySize >= MIN_PARTY && partySize <= MAX_PARTY;
    }

    private static void requireValidPartySize(int partySize) {
        if (!isValidPartySize(partySize)) {
            throw new IllegalArgumentException("partySize out of range: " + partySize);
        }
    }

    /**
     * 상태 전이. 허용되지 않으면 IllegalStateException — 서비스가 409 INVALID_STATE_TRANSITION 으로 바꾼다.
     */
    public void transitionTo(WaitingStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(status + " -> " + next);
        }
        this.status = next;
        if (next == WaitingStatus.CALLED) {
            this.calledAt = now;
        }
        if (next.isClosed()) {
            this.closedAt = now;
        }
    }

    public void changePartySize(int partySize) {
        if (!status.canChangePartySize()) {
            throw new IllegalStateException("cannot change party size in " + status);
        }
        requireValidPartySize(partySize);
        this.partySize = partySize;
    }

    public void deferTo(long newQueueOrder) {
        if (!status.canDefer()) {
            throw new IllegalStateException("cannot defer in " + status);
        }
        this.queueOrder = newQueueOrder;
        this.deferCount++;
    }

    public void updateEta(int etaMin, Instant now) {
        this.etaMin = Math.max(0, etaMin);
        this.etaUpdatedAt = now;
    }

    public boolean isOwnedBy(long deviceId) {
        return this.deviceId != null && this.deviceId == deviceId;
    }
}
