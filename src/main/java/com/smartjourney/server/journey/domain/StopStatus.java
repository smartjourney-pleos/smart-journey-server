package com.smartjourney.server.journey.domain;

/**
 * 코스의 한 지점.
 * PLANNED = 아직 웨이팅 전 (차량 앱에서는 PLANNED/PROMPTED 로 골든타임을 기다리는 중)
 * WAITING = 웨이팅 접수됨 (waitingId 연결)
 * DONE    = 착석·노쇼·취소로 끝남
 * SKIPPED = 사용자가 코스에서 뺌 / 영업 종료로 제외
 */
public enum StopStatus {
    PLANNED,
    WAITING,
    DONE,
    SKIPPED
}
