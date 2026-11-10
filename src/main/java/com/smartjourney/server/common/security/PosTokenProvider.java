package com.smartjourney.server.common.security;

import com.smartjourney.server.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 매장 POS 로그인 토큰 (HS256 JWT, subject = storeId).
 */
@Component
public class PosTokenProvider {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public PosTokenProvider(AppProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.auth().posTokenTtl();
        this.clock = clock;
    }

    public IssuedToken issue(long storeId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(String.valueOf(storeId))
                .claim(CLAIM_ROLE, PosPrincipal.ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Optional<PosPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PosPrincipal.ROLE.equals(claims.get(CLAIM_ROLE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new PosPrincipal(Long.parseLong(claims.getSubject())));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String accessToken, Instant expiresAt) {
    }
}
