package com.smartjourney.server.store.domain;

import com.smartjourney.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장. 좌표·POI 는 차량 앱이 NaviHelper RouteInfo / RequestWaypointInfo 를 만들 때 그대로 쓴다.
 */
@Getter
@Entity
@Table(name = "store")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "poi_id", length = 100)
    private String poiId;

    @Column(name = "poi_sub_id", nullable = false, length = 20)
    private String poiSubId;

    /** 팀당 소요 분. 예상 대기 = 앞 팀 수 × 이 값 */
    @Column(name = "minutes_per_team", nullable = false)
    private int minutesPerTeam;

    /** 도착 여유(분). 골든타임 = 남은 ETA ≤ 예상 대기 − 이 값 */
    @Column(name = "arrival_buffer_min", nullable = false)
    private int arrivalBufferMin;

    /** 점주가 직접 넣은 대기시간. 있으면 계산값보다 우선 */
    @Column(name = "manual_wait_min")
    private Integer manualWaitMin;

    @Column(name = "max_defers", nullable = false)
    private int maxDefers;

    @Column(name = "avg_stay_min", nullable = false)
    private int avgStayMin;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "pos_pin_hash", nullable = false, length = 100)
    private String posPinHash;

    /** POS 설정 화면(P-05). null 인 값은 바꾸지 않는다. manualWaitMin 은 clearManualWait 로만 지운다. */
    public void updateSettings(Integer minutesPerTeam, Integer arrivalBufferMin, Integer manualWaitMin,
                               boolean clearManualWait) {
        if (minutesPerTeam != null) {
            this.minutesPerTeam = minutesPerTeam;
        }
        if (arrivalBufferMin != null) {
            this.arrivalBufferMin = arrivalBufferMin;
        }
        if (clearManualWait) {
            this.manualWaitMin = null;
        } else if (manualWaitMin != null) {
            this.manualWaitMin = manualWaitMin;
        }
    }

    public boolean isOpenAt(LocalTime time) {
        if (openTime == null || closeTime == null) {
            return true;
        }
        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }
}
