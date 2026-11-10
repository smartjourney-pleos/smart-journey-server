package com.smartjourney.server.journey.domain;

/**
 * 차량 앱 안에서만 일어나는 일 — 서버가 직접 볼 수 없어 앱이 보고한다 (KPI 산출용, P1).
 * PRD "핵심 지표": 인원 제안 수락률, 골든타임 알림 정확도, 단일 승인 처리 비율, 주행 중 조작 횟수.
 */
public enum JourneyEventType {
    PARTY_SUGGESTED,        // 센서가 인원 제안
    PARTY_EDITED,           // 사용자가 수정
    PROMPT_SHOWN,           // 골든타임 승인 카드 노출
    PROMPT_DECLINED,        // "나중에"
    PROMPT_TIMEOUT,         // 60초 무응답
    PARTY_CHANGE_SUGGESTED, // 다음 매장 인원 변경 제안 (기능 1.3)
    DELAY_DETECTED,         // ETA 급증 감지 (기능 4.1)
    VOICE_COMMAND,          // Gleo 함수 호출
    RESTRICTED_ACTION_BLOCKED // 주행 중 잠긴 기능 진입 시도 (기능 5.2)
}
