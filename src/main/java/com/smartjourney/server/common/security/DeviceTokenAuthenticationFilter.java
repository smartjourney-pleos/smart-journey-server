package com.smartjourney.server.common.security;

import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.device.domain.Device;
import com.smartjourney.server.device.domain.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 차량 앱 인증: {@code X-Device-Token} 헤더 → {@link DevicePrincipal}.
 *
 * <p>정책 4 (동의 없이는 외부 전송 금지)를 여기서 한 번에 강제한다.
 * 동의를 철회한 기기는 동의 관련 API 외에는 전부 403 CONSENT_REQUIRED.
 *
 * <p>빈으로 등록하지 않는다 (서블릿 필터로 중복 등록되는 것을 막기 위해 SecurityConfig 에서 직접 생성).
 */
public class DeviceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEVICE_API_PREFIX = "/api/v1/";
    private static final String POS_API_PREFIX = "/api/v1/pos/";
    private static final String DEVICE_ME_PATH = "/api/v1/devices/me";

    private final DeviceRepository deviceRepository;
    private final ErrorResponseWriter errorWriter;

    public DeviceTokenAuthenticationFilter(DeviceRepository deviceRepository, ErrorResponseWriter errorWriter) {
        this.deviceRepository = deviceRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(DEVICE_API_PREFIX) || uri.startsWith(POS_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(AuthHeaders.DEVICE_TOKEN);
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);   // 인증 없이 통과 → 보호된 경로면 EntryPoint 가 401
            return;
        }

        Optional<Device> found = deviceRepository.findByDeviceToken(token);
        if (found.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Device device = found.get();
        if (device.isRevoked() && !isConsentManagementRequest(request)) {
            errorWriter.write(response, ErrorCode.CONSENT_REQUIRED);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                new DevicePrincipal(device.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + DevicePrincipal.ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    /** 철회 상태에서도 허용: 내 동의 상태 조회, 재동의, (중복) 철회 */
    private boolean isConsentManagementRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.equals(DEVICE_ME_PATH) && HttpMethod.GET.matches(method)) {
            return true;
        }
        return uri.equals(DEVICE_ME_PATH + "/consent")
                && (HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method));
    }
}
