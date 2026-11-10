package com.smartjourney.server.ticket.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 호출 시 차량에 내려주는 번호표 QR 문자열 (V-11, C-14). 순수 Java — 단위 테스트 대상.
 *
 * <pre>
 * payload = "SJ1." + base64url("{waitingId}.{storeId}.{ticketNo}.{expiresAtEpochSec}") + "." + base64url(HMAC-SHA256)
 * </pre>
 * 개인정보는 넣지 않는다. 매장 POS 가 스캔하면 서버가 서명·만료를 검증한다 (P2).
 */
public final class QrTicketCodec {

    private static final String PREFIX = "SJ1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public QrTicketCodec(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("QR secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(QrTicket ticket) {
        String body = ticket.waitingId() + "." + ticket.storeId() + "." + ticket.ticketNo() + "."
                + ticket.expiresAt().getEpochSecond();
        String encodedBody = ENCODER.encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return PREFIX + "." + encodedBody + "." + ENCODER.encodeToString(sign(encodedBody));
    }

    /** 서명이 맞고 만료 전이면 티켓, 아니면 empty */
    public Optional<QrTicket> decode(String payload, Instant now) {
        if (payload == null) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            String[] fields = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8).split("\\.");
            if (fields.length != 4) {
                return Optional.empty();
            }
            QrTicket ticket = new QrTicket(
                    Long.parseLong(fields[0]),
                    Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]),
                    Instant.ofEpochSecond(Long.parseLong(fields[3])));
            return ticket.expiresAt().isAfter(now) ? Optional.of(ticket) : Optional.empty();
        } catch (IllegalArgumentException e) {   // Base64·숫자 파싱 실패 (NumberFormatException 포함)
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public record QrTicket(long waitingId, long storeId, int ticketNo, Instant expiresAt) {
    }
}
