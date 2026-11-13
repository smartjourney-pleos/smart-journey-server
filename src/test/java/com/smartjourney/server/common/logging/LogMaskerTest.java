package com.smartjourney.server.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskerTest {

    @Test
    @DisplayName("기기 토큰·Bearer·PIN·JWT·QR 은 값만 가린다")
    void masks() {
        assertThat(LogMasker.mask("X-Device-Token=abcDEF123_-xyz path=/api/v1/stores"))
                .isEqualTo("X-Device-Token=*** path=/api/v1/stores");
        assertThat(LogMasker.mask("Authorization: Bearer abc.def.ghi"))
                .isEqualTo("Authorization: Bearer ***");
        assertThat(LogMasker.mask("{\"storeId\":1,\"pin\":\"1234\"}"))
                .isEqualTo("{\"storeId\":1,\"pin\":\"***\"}");
        assertThat(LogMasker.mask("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig_nature-xx end"))
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9").contains("***jwt");
        assertThat(LogMasker.mask("qr SJ1.MTIzLjEuMi4x.c2lnbmF0dXJl ok"))
                .isEqualTo("qr SJ1.*** ok");
    }

    @Test
    @DisplayName("평범한 메시지는 그대로")
    void untouched() {
        String msg = "STOMP QUEUE_UPDATED -> /topic/stores/1/queue";
        assertThat(LogMasker.mask(msg)).isEqualTo(msg);
        assertThat(LogMasker.mask(null)).isNull();
    }
}
