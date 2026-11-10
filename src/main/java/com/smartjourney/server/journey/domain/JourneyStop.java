package com.smartjourney.server.journey.domain;

import com.smartjourney.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "journey_stop")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyStop extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    /** 1..3. 내비 입력 순서 (1·2 = 경유지 FIRST·SECOND, 마지막 = 목적지) */
    @Column(nullable = false)
    private int seq;

    @Column(name = "planned_stay_min")
    private Integer plannedStayMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StopStatus status;

    @Column(name = "waiting_id")
    private Long waitingId;

    static JourneyStop planned(Journey journey, long storeId, int seq) {
        JourneyStop s = new JourneyStop();
        s.journey = journey;
        s.storeId = storeId;
        s.seq = seq;
        s.status = StopStatus.PLANNED;
        return s;
    }

    public void linkWaiting(long waitingId) {
        this.waitingId = waitingId;
        this.status = StopStatus.WAITING;
    }

    public void markDone() {
        this.status = StopStatus.DONE;
    }

    public void skip() {
        this.status = StopStatus.SKIPPED;
    }

    void reorder(int seq) {
        this.seq = seq;
    }
}
