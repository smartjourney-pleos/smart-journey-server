package com.smartjourney.server.waiting.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 서버가 저장하는 웨이팅 상태 (개발 명세서 5.1 상태 머신).
 *
 * <pre>
 * REGISTERED ─▶ ARRIVED ─▶ CALLED ─▶ SEATED
 *     │            │          └────▶ NO_SHOW
 *     ├──────────────────────▶ CALLED        (도착 전에 점주가 호출)
 *     └──▶ CANCELLED ◀──── ARRIVED
 * </pre>
 *
 * PLANNED / PROMPTED 는 차량 앱 안에서만 존재한다 (골든타임 전 단계 — 서버에는 JourneyStop.PLANNED 로 남는다).
 * 인원 변경·순번 미루기는 상태를 바꾸지 않는다 (REGISTERED → REGISTERED).
 */
public enum WaitingStatus {
    REGISTERED,
    ARRIVED,
    CALLED,
    SEATED,
    NO_SHOW,
    CANCELLED;

    /** 대기열에 살아 있는 상태 */
    public static final Set<WaitingStatus> ACTIVE = EnumSet.of(REGISTERED, ARRIVED, CALLED);

    /** 예상 대기 계산에서 "내 앞 팀" 으로 세는 상태 (명세서 6.1: REGISTERED·ARRIVED) */
    public static final Set<WaitingStatus> COUNTED_AHEAD = EnumSet.of(REGISTERED, ARRIVED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isClosed() {
        return !isActive();
    }

    public boolean canTransitionTo(WaitingStatus next) {
        return switch (this) {
            case REGISTERED -> next == ARRIVED || next == CALLED || next == CANCELLED;
            case ARRIVED -> next == CALLED || next == CANCELLED;
            case CALLED -> next == SEATED || next == NO_SHOW;
            case SEATED, NO_SHOW, CANCELLED -> false;
        };
    }

    /** 인원 변경 가능: 호출 전까지만 (호출 후엔 409 WAITING_ALREADY_CALLED) */
    public boolean canChangePartySize() {
        return this == REGISTERED || this == ARRIVED;
    }

    /** 순번 미루기 가능: 아직 도착 전 */
    public boolean canDefer() {
        return this == REGISTERED;
    }
}
