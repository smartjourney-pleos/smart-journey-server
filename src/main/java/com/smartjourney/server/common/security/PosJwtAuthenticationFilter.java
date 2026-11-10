package com.smartjourney.server.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 매장 POS 인증: {@code Authorization: Bearer <JWT>} → {@link PosPrincipal}. /api/v1/pos/** 에만 적용.
 * 빈으로 등록하지 않는다 (SecurityConfig 에서 직접 생성).
 */
public class PosJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String POS_API_PREFIX = "/api/v1/pos/";

    private final PosTokenProvider tokenProvider;

    public PosJwtAuthenticationFilter(PosTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(POS_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(AuthHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(AuthHeaders.BEARER_PREFIX)) {
            String token = header.substring(AuthHeaders.BEARER_PREFIX.length());
            tokenProvider.parse(token).ifPresent(principal -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + PosPrincipal.ROLE)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
