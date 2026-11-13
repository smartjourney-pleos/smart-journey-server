package com.smartjourney.server.common.config;

import com.smartjourney.server.common.security.DevicePrincipal;
import com.smartjourney.server.common.security.DeviceTokenAuthenticationFilter;
import com.smartjourney.server.common.security.ErrorResponseWriter;
import com.smartjourney.server.common.security.JsonSecurityHandlers;
import com.smartjourney.server.common.security.PosJwtAuthenticationFilter;
import com.smartjourney.server.common.security.PosPrincipal;
import com.smartjourney.server.common.security.PosTokenProvider;
import com.smartjourney.server.device.domain.DeviceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증 2종.
 * <ul>
 *   <li>차량 앱: {@code X-Device-Token} (동의 시 발급) → ROLE_DEVICE</li>
 *   <li>매장 POS: {@code Authorization: Bearer <JWT>} (매장 PIN 로그인) → ROLE_POS</li>
 * </ul>
 * 세션·쿠키를 쓰지 않는 stateless API.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DeviceRepository deviceRepository;
    private final PosTokenProvider posTokenProvider;
    private final ErrorResponseWriter errorResponseWriter;
    private final JsonSecurityHandlers jsonSecurityHandlers;
    private final AppProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jsonSecurityHandlers)
                        .accessDeniedHandler(jsonSecurityHandlers))
                .authorizeHttpRequests(auth -> auth
                        // 공개
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/privacy", "/privacy.html").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()            // 인증은 STOMP CONNECT 에서
                        .requestMatchers(HttpMethod.GET, "/api/v1/consent/notice").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/devices").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pos/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pos/stores").permitAll()
                        // 운영 지표: 컨트롤러가 X-Ops-Token 검사 / 대시보드 정적 페이지
                        .requestMatchers("/api/v1/ops/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/ops/kpi.html").permitAll()
                        // 매장 POS
                        .requestMatchers("/api/v1/pos/**").hasRole(PosPrincipal.ROLE)
                        // 차량 앱
                        .requestMatchers("/api/v1/**").hasRole(DevicePrincipal.ROLE)
                        .anyRequest().denyAll())
                .addFilterBefore(new PosJwtAuthenticationFilter(posTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new DeviceTokenAuthenticationFilter(deviceRepository, errorResponseWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.web().allowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
