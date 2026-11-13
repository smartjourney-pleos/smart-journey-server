package com.smartjourney.server.journey.domain;

import com.smartjourney.server.common.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여정 = 오늘의 코스 (최대 3곳). "여정 시작" 시 생성되고 마지막 매장 착석·취소 또는 "여정 끝내기" 시 종료.
 */
@Getter
@Entity
@Table(name = "journey")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Journey extends BaseTimeEntity {

    /** NaviHelper 경유지 FIRST·SECOND 2개 + 목적지 1개 */
    public static final int MAX_STOPS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_source", nullable = false, length = 10)
    private PartySource partySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JourneyStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<JourneyStop> stops = new ArrayList<>();

    public static Journey start(long deviceId, int partySize, PartySource partySource,
                                List<Long> storeIdsInOrder, Instant now) {
        if (storeIdsInOrder.isEmpty() || storeIdsInOrder.size() > MAX_STOPS) {
            throw new IllegalArgumentException("stops must be 1.." + MAX_STOPS);
        }
        Journey j = new Journey();
        j.deviceId = deviceId;
        j.partySize = partySize;
        j.partySource = partySource;
        j.status = JourneyStatus.ACTIVE;
        j.startedAt = now;
        for (int i = 0; i < storeIdsInOrder.size(); i++) {
            j.stops.add(JourneyStop.planned(j, storeIdsInOrder.get(i), i + 1));
        }
        return j;
    }

    public boolean isOwnedBy(long deviceId) {
        return this.deviceId == deviceId;
    }

    public boolean isActive() {
        return status == JourneyStatus.ACTIVE;
    }

    public void changeParty(int partySize, PartySource source) {
        this.partySize = partySize;
        this.partySource = source;
    }

    public Optional<JourneyStop> stopForStore(long storeId) {
        return stops.stream().filter(s -> s.getStoreId() == storeId).findFirst();
    }

    public Optional<JourneyStop> stopForWaiting(long waitingId) {
        return stops.stream().filter(s -> s.getWaitingId() != null && s.getWaitingId() == waitingId).findFirst();
    }

    /** 아직 웨이팅 전이거나 웨이팅 중인 매장이 남아 있는지 */
    public boolean hasPendingStops() {
        return stops.stream().anyMatch(s -> s.getStatus() == StopStatus.PLANNED || s.getStatus() == StopStatus.WAITING);
    }

    /**
     * 순서 변경 1단계: seq 를 임시 값(원래 + offset)으로 옮긴다.
     * (journey_id, seq) 유니크 제약 때문에 바로 바꾸면 중간에 겹친다 → 서비스가 flush 후 {@link #applyOrder} 호출
     */
    public void shiftSeqs(int offset) {
        stops.forEach(s -> s.reorder(s.getSeq() + offset));
    }

    /** 순서 변경 2단계. storeIdsInOrder 는 기존 매장들의 순열이어야 한다 */
    public void applyOrder(List<Long> storeIdsInOrder) {
        for (int i = 0; i < storeIdsInOrder.size(); i++) {
            long storeId = storeIdsInOrder.get(i);
            JourneyStop stop = stopForStore(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("not in journey: " + storeId));
            stop.reorder(i + 1);
        }
        stops.sort(java.util.Comparator.comparingInt(JourneyStop::getSeq));
    }

    public void end(Instant now) {
        if (status == JourneyStatus.ACTIVE) {
            status = JourneyStatus.ENDED;
            endedAt = now;
        }
    }
}
