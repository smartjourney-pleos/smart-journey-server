package com.smartjourney.server.waiting.domain;

import static com.smartjourney.server.waiting.domain.WaitingStatus.ARRIVED;
import static com.smartjourney.server.waiting.domain.WaitingStatus.CALLED;
import static com.smartjourney.server.waiting.domain.WaitingStatus.CANCELLED;
import static com.smartjourney.server.waiting.domain.WaitingStatus.NO_SHOW;
import static com.smartjourney.server.waiting.domain.WaitingStatus.REGISTERED;
import static com.smartjourney.server.waiting.domain.WaitingStatus.SEATED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WaitingStatusTest {

    @Test
    @DisplayName("명세서 5.1 상태 머신의 허용 전이")
    void allowed() {
        assertThat(REGISTERED.canTransitionTo(ARRIVED)).isTrue();
        assertThat(REGISTERED.canTransitionTo(CALLED)).isTrue();     // 도착 전 호출
        assertThat(REGISTERED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(ARRIVED.canTransitionTo(CALLED)).isTrue();
        assertThat(CALLED.canTransitionTo(SEATED)).isTrue();
        assertThat(CALLED.canTransitionTo(NO_SHOW)).isTrue();
    }

    @Test
    @DisplayName("끝난 상태에서는 어디로도 못 간다, 호출 후 취소·되돌리기 불가")
    void forbidden() {
        for (WaitingStatus closed : new WaitingStatus[] {SEATED, NO_SHOW, CANCELLED}) {
            for (WaitingStatus next : WaitingStatus.values()) {
                assertThat(closed.canTransitionTo(next)).isFalse();
            }
        }
        assertThat(CALLED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(ARRIVED.canTransitionTo(REGISTERED)).isFalse();
        assertThat(REGISTERED.canTransitionTo(NO_SHOW)).isFalse();
    }

    @Test
    @DisplayName("인원 변경은 호출 전까지, 미루기는 도착 전까지")
    void partyAndDefer() {
        assertThat(REGISTERED.canChangePartySize()).isTrue();
        assertThat(ARRIVED.canChangePartySize()).isTrue();
        assertThat(CALLED.canChangePartySize()).isFalse();
        assertThat(REGISTERED.canDefer()).isTrue();
        assertThat(ARRIVED.canDefer()).isFalse();
    }

    @Test
    @DisplayName("예상 대기 계산엔 REGISTERED·ARRIVED 만 앞 팀으로 센다")
    void countedAhead() {
        assertThat(WaitingStatus.COUNTED_AHEAD).containsExactlyInAnyOrder(REGISTERED, ARRIVED);
    }
}
