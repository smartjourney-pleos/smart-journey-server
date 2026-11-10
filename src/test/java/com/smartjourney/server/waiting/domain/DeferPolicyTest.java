package com.smartjourney.server.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeferPolicyTest {

    @Test
    @DisplayName("미루기 횟수는 maxDefers 미만일 때만")
    void limit() {
        assertThat(DeferPolicy.canDefer(0, 2)).isTrue();
        assertThat(DeferPolicy.canDefer(1, 2)).isTrue();
        assertThat(DeferPolicy.canDefer(2, 2)).isFalse();
    }

    @Test
    @DisplayName("15분 늦으면 팀당 5분 매장에서 3팀 뒤로, 최소 1팀")
    void teams() {
        assertThat(DeferPolicy.teamsToPushBack(15, 5)).isEqualTo(3);
        assertThat(DeferPolicy.teamsToPushBack(11, 5)).isEqualTo(3);
        assertThat(DeferPolicy.teamsToPushBack(0, 5)).isEqualTo(1);
        assertThat(DeferPolicy.teamsToPushBack(10, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 팀 사이로 끼워 넣는다")
    void insertBetween() {
        long next = DeferPolicy.newQueueOrder(100, List.of(200L, 300L, 400L), 2);
        assertThat(next).isGreaterThan(300).isLessThan(400);
    }

    @Test
    @DisplayName("뒤 팀이 부족하면 맨 뒤로, 아무도 없으면 그대로")
    void tail() {
        assertThat(DeferPolicy.newQueueOrder(100, List.of(200L), 3)).isEqualTo(201);
        assertThat(DeferPolicy.newQueueOrder(100, List.of(), 3)).isEqualTo(100);
    }
}
