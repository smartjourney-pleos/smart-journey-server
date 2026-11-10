package com.smartjourney.server.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueueCalculatorTest {

    @Test
    @DisplayName("예상 대기 = 앞 팀 수 × 팀당 분")
    void computedWait() {
        assertThat(QueueCalculator.estimatedWaitMin(null, 6, 5)).isEqualTo(30);
        assertThat(QueueCalculator.estimatedWaitMin(null, 0, 5)).isZero();
    }

    @Test
    @DisplayName("점주가 직접 넣은 대기시간이 계산값보다 우선")
    void manualOverride() {
        assertThat(QueueCalculator.estimatedWaitMin(45, 2, 5)).isEqualTo(45);
        assertThat(QueueCalculator.estimatedWaitMin(-3, 2, 5)).isZero();
    }

    @Test
    void rejectsNegativeInput() {
        assertThatThrownBy(() -> QueueCalculator.estimatedWaitMin(null, -1, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PRD 예시: 대기 30분이면 입장 예정은 지금+30분")
    void entryAt() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        assertThat(QueueCalculator.estimatedEntryAt(now, 30)).isEqualTo(Instant.parse("2026-11-14T03:30:00Z"));
    }

    @Test
    @DisplayName("queue_order 는 등록 시각 순 + 사이에 끼워 넣을 여유")
    void initialOrderKeepsGap() {
        long a = QueueCalculator.initialQueueOrder(Instant.ofEpochMilli(1_000));
        long b = QueueCalculator.initialQueueOrder(Instant.ofEpochMilli(1_001));
        assertThat(b - a).isEqualTo(1_000);
    }
}
