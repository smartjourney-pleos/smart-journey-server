package com.smartjourney.server.pos.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PinAttemptLimiterTest {

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 Clock */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-12-03T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void plus(Duration d) {
            now = now.plus(d);
        }
    }

    @Test
    @DisplayName("5분 안에 5번 틀리면 5분 잠금 → 지나면 풀림")
    void locksAfterFiveFailures() {
        MutableClock clock = new MutableClock();
        PinAttemptLimiter limiter = new PinAttemptLimiter(clock);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(1L);
        }
        assertThat(limiter.isLocked(1L)).isFalse();
        limiter.recordFailure(1L);
        assertThat(limiter.isLocked(1L)).isTrue();
        assertThat(limiter.isLocked(2L)).as("다른 매장은 상관없음").isFalse();

        clock.plus(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(limiter.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("실패 사이 간격이 5분을 넘으면 오래된 실패는 세지 않는다 / 성공하면 초기화")
    void slidingWindowAndReset() {
        MutableClock clock = new MutableClock();
        PinAttemptLimiter limiter = new PinAttemptLimiter(clock);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(1L);
        }
        clock.plus(Duration.ofMinutes(6));
        limiter.recordFailure(1L);
        assertThat(limiter.isLocked(1L)).isFalse();

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(1L);
        }
        limiter.recordSuccess(1L);
        limiter.recordFailure(1L);
        assertThat(limiter.isLocked(1L)).isFalse();
    }
}
