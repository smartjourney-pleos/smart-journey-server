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

    public void end(Instant now) {
        if (status == JourneyStatus.ACTIVE) {
            status = JourneyStatus.ENDED;
            endedAt = now;
        }
    }
}
