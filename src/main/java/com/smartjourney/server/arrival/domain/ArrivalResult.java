package com.smartjourney.server.arrival.domain;

/**
 * 하차 확인 결과 (정책 7). POS 배지: CONFIRMED 초록 / MANUAL 회색 "고객 확인" / UNCONFIRMED 노랑 "인원 미확인".
 * 미확정을 성공으로 표시하지 않는다.
 */
public enum ArrivalResult {
    /** 도착 이벤트 + 정차 + 문 열림·닫힘 + 점유 감소가 안정화 5초 유지 + 재확인 일치 */
    CONFIRMED,
    /** 3분 타임아웃 / 센서 미지원 → 사용자가 "도착했어요" 버튼 */
    MANUAL,
    /** 확정 전 시동 꺼짐·앱 종료 → 도착만 알림, 인원 미확인 */
    UNCONFIRMED
}
