package com.smartjourney.server.waiting.domain;

/**
 * VEHICLE = 스마트 저니 차량 앱으로 접수 (POS 에 🚗 차량 인증 채널로 표시 — 기능 6.1 비즈니스 규칙)
 * WALK_IN = 현장 대기 팀 (POS 데모 패널 +1/−1 로도 생성)
 */
public enum WaitingChannel {
    VEHICLE,
    WALK_IN
}
