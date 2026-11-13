package com.smartjourney.server.route.domain;

/**
 * 매장 ↔ 매장 자동차 소요시간(분). 매장 좌표만 쓴다 — 차량 위치는 서버에 오지 않는다 (정책 4).
 */
public interface TravelTimeProvider {

    String name();

    /** @throws RuntimeException 키 없음·외부 API 실패 → 호출하는 쪽이 입력 순서로 폴백 */
    int minutes(double fromLat, double fromLng, double toLat, double toLng);
}
