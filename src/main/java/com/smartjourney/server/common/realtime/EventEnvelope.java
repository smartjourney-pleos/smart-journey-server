package com.smartjourney.server.common.realtime;

import java.time.Instant;

/**
 * 모든 STOMP 메시지의 공통 봉투: {@code {type, eventId, occurredAt, payload}}.
 * 클라이언트는 eventId 로 중복 수신을 무시한다.
 */
public record EventEnvelope(RealtimeEventType type, String eventId, Instant occurredAt, Object payload) {
}
