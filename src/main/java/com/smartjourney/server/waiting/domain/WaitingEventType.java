package com.smartjourney.server.waiting.domain;

/**
 * 웨이팅 감사 로그 (POS 팀 상세 P-03 이벤트 기록 + KPI 산출).
 */
public enum WaitingEventType {
    REGISTERED,
    PARTY_SIZE_CHANGED,
    DEFERRED,
    ETA_UPDATED,
    ARRIVAL_REPORTED,
    CALLED,
    SEATED,
    NO_SHOW,
    CANCELLED
}
