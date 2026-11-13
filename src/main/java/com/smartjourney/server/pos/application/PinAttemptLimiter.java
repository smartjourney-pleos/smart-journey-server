package com.smartjourney.server.pos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * POS PIN 연속 실패 잠금 (보안 가이드, 4-7). 매장별로 최근 5분 안에 5번 틀리면 5분 잠금.
 * 서버 1대 · 4자리 PIN 이라 메모리 보관으로 충분하다 (재시작하면 풀린다).
 */
@Component
@RequiredArgsConstructor
public class PinAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(5);
    static final Duration LOCK = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public boolean isLocked(long storeId) {
        State state = states.get(storeId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.lockedUntil != null && clock.instant().isBefore(state.lockedUntil);
        }
    }

    public void recordFailure(long storeId) {
        Instant now = clock.instant();
        State state = states.computeIfAbsent(storeId, id -> new State());
        synchronized (state) {
            while (!state.failures.isEmpty() && state.failures.peekFirst().isBefore(now.minus(WINDOW))) {
                state.failures.pollFirst();
            }
            state.failures.addLast(now);
            if (state.failures.size() >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCK);
                state.failures.clear();
            }
        }
    }

    public void recordSuccess(long storeId) {
        states.remove(storeId);
    }

    private static final class State {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;
    }
}
