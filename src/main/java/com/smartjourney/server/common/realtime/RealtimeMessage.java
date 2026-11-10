package com.smartjourney.server.common.realtime;

/**
 * 서비스가 "커밋되면 보내 줘" 라고 남기는 메시지.
 * {@link org.springframework.context.ApplicationEventPublisher} 로 발행하면 {@link RealtimeRelay} 가 커밋 후 전송한다.
 * (롤백된 변경을 클라이언트가 먼저 받는 일을 막기 위함)
 */
public record RealtimeMessage(String destination, RealtimeEventType type, Object payload) {
}
