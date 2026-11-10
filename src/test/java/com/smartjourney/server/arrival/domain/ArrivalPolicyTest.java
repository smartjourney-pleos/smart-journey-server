package com.smartjourney.server.arrival.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArrivalPolicyTest {

    @Test
    @DisplayName("PRD 장면 4: 4명 중 운전자만 남으면 3/4 하차")
    void driverStays() {
        assertThat(ArrivalPolicy.alightedFrom(4, 1)).isEqualTo(3);
        assertThat(ArrivalPolicy.alightedFrom(4, 0)).isEqualTo(4);
        assertThat(ArrivalPolicy.alightedFrom(4, 5)).isZero();   // 짐 오탐 등으로 점유가 더 많아도 음수 X
    }

    @Test
    @DisplayName("CONFIRMED 는 1..N 명이어야 한다 (아무도 안 내렸는데 확정은 모순)")
    void confirmed() {
        assertThat(ArrivalPolicy.violation(ArrivalResult.CONFIRMED, 3, 4)).isEmpty();
        assertThat(ArrivalPolicy.violation(ArrivalResult.CONFIRMED, 0, 4)).isPresent();
        assertThat(ArrivalPolicy.violation(ArrivalResult.CONFIRMED, null, 4)).isPresent();
        assertThat(ArrivalPolicy.violation(ArrivalResult.CONFIRMED, 5, 4)).isPresent();
    }

    @Test
    @DisplayName("미확정은 인원을 싣지 않는다 — 미확정을 성공으로 보이지 않게")
    void unconfirmed() {
        assertThat(ArrivalPolicy.violation(ArrivalResult.UNCONFIRMED, null, 4)).isEmpty();
        assertThat(ArrivalPolicy.violation(ArrivalResult.UNCONFIRMED, 2, 4)).isPresent();
    }

    @Test
    void manual() {
        assertThat(ArrivalPolicy.violation(ArrivalResult.MANUAL, null, 4)).isEmpty();
        assertThat(ArrivalPolicy.violation(ArrivalResult.MANUAL, 4, 4)).isEmpty();
        assertThat(ArrivalPolicy.violation(ArrivalResult.MANUAL, 7, 4)).isPresent();
    }
}
