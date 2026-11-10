package com.smartjourney.server.journey.domain;

/**
 * 인원 출처 (기능 1.1). 센서 산출값은 제안값이고 사용자가 고친 값(MANUAL)이 항상 우선한다.
 * KPI: 인원 제안 수락률 = SENSOR 비율.
 */
public enum PartySource {
    SENSOR,
    MANUAL
}
