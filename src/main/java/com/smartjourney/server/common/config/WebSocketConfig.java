package com.smartjourney.server.common.config;

import com.smartjourney.server.common.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket.
 *
 * <ul>
 *   <li>엔드포인트: {@code /ws} (SockJS 없음 — Krossbow / @stomp/stompjs 가 순수 WebSocket 지원)</li>
 *   <li>구독: {@code /topic/stores/{storeId}/queue}, {@code /topic/waitings/{waitingId}}, {@code /topic/pos/{storeId}}</li>
 *   <li>인증: CONNECT 프레임 헤더 (X-Device-Token 또는 Authorization: Bearer). URL 쿼리에 토큰 금지 — Pleos 앱 보안 가이드</li>
 * </ul>
 * 데모 규모라 인메모리 Simple Broker 로 충분하다 (서버 1대).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.web().allowedOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                // 차량 앱이 터널·음영 구간에서 끊긴 걸 빨리 알아채도록 heartbeat 10초
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    private org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
