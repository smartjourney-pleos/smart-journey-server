package com.smartjourney.server.common.security;

import com.smartjourney.server.device.domain.Device;
import com.smartjourney.server.device.domain.DeviceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * STOMP 인증·인가.
 * <ul>
 *   <li>CONNECT : 네이티브 헤더 {@code X-Device-Token} 또는 {@code Authorization: Bearer} 필수</li>
 *   <li>SUBSCRIBE : {@link SubscriptionAuthorizer} 규칙</li>
 *   <li>SEND : 사용하지 않음 (명령은 REST) → 거부</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final DeviceRepository deviceRepository;
    private final PosTokenProvider posTokenProvider;
    private final SubscriptionAuthorizer subscriptionAuthorizer;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            UsernamePasswordAuthenticationToken user = authenticate(accessor)
                    .orElseThrow(() -> new MessagingException("UNAUTHORIZED"));
            accessor.setUser(user);
        } else if (command == StompCommand.SUBSCRIBE) {
            Object principal = accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                    ? token.getPrincipal() : null;
            if (!subscriptionAuthorizer.canSubscribe(principal, accessor.getDestination())) {
                throw new MessagingException("FORBIDDEN: " + accessor.getDestination());
            }
        } else if (command == StompCommand.SEND) {
            throw new MessagingException("SEND is not supported. Use REST API.");
        }
        return message;
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticate(StompHeaderAccessor accessor) {
        String deviceToken = accessor.getFirstNativeHeader(AuthHeaders.DEVICE_TOKEN);
        if (deviceToken != null && !deviceToken.isBlank()) {
            return deviceRepository.findByDeviceToken(deviceToken)
                    .filter(device -> !device.isRevoked())
                    .map(Device::getId)
                    .map(id -> new UsernamePasswordAuthenticationToken(new DevicePrincipal(id), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + DevicePrincipal.ROLE))));
        }

        String authorization = accessor.getFirstNativeHeader(AuthHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(AuthHeaders.BEARER_PREFIX)) {
            return posTokenProvider.parse(authorization.substring(AuthHeaders.BEARER_PREFIX.length()))
                    .map(pos -> new UsernamePasswordAuthenticationToken(pos, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + PosPrincipal.ROLE))));
        }
        return Optional.empty();
    }
}
