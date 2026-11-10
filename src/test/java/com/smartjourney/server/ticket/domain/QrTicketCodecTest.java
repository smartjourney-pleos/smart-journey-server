package com.smartjourney.server.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartjourney.server.ticket.domain.QrTicketCodec.QrTicket;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QrTicketCodecTest {

    private static final String SECRET = "test-only-qr-secret-0123456789abcdef0123456789abcdef";
    private final QrTicketCodec codec = new QrTicketCodec(SECRET);
    private final Instant now = Instant.parse("2026-12-03T05:00:00Z");

    @Test
    void roundTrip() {
        QrTicket ticket = new QrTicket(42, 1, 17, now.plusSeconds(1800));
        String payload = codec.encode(ticket);
        assertThat(payload).startsWith("SJ1.");
        assertThat(codec.decode(payload, now)).contains(ticket);
    }

    @Test
    void rejectsExpired() {
        String payload = codec.encode(new QrTicket(42, 1, 17, now.minusSeconds(1)));
        assertThat(codec.decode(payload, now)).isEmpty();
    }

    @Test
    void rejectsTampered() {
        String payload = codec.encode(new QrTicket(42, 1, 17, now.plusSeconds(1800)));
        String forgedBody = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("43.1.17." + now.plusSeconds(1800).getEpochSecond()).getBytes());
        String[] parts = payload.split("\\.");
        assertThat(codec.decode(parts[0] + "." + forgedBody + "." + parts[2], now)).isEmpty();
        assertThat(codec.decode("garbage", now)).isEmpty();
        assertThat(new QrTicketCodec(SECRET + "x").decode(payload, now)).isEmpty();
    }
}
