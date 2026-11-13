package com.smartjourney.server.common.logging;

import java.util.regex.Pattern;

/**
 * 로그에 비밀값이 남지 않게 가린다 (보안 가이드 4-7). 순수 함수 — 단위 테스트 대상.
 *
 * <ul>
 *   <li>{@code X-Device-Token=…}, {@code Authorization: Bearer …}, {@code pin=…}, {@code "token":"…"} → 값만 ***</li>
 *   <li>JWT 모양(eyJ….….…) → ***jwt</li>
 *   <li>번호표 QR(SJ1.….…) → SJ1.***</li>
 * </ul>
 */
public final class LogMasker {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(x-device-token|device[_-]?token|authorization|access[_-]?token|jwt[_-]?secret|qr[_-]?secret"
                    + "|kakao[_-]?rest[_-]?api[_-]?key|ops[_-]?token|password|pin|token)"
                    + "([\"']?\\s*[:=]\\s*[\"']?)(bearer\\s+)?[^\\s,\"'}&\\]]+");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]{5,}\\.[\\w-]{5,}\\.[\\w-]{5,}");
    private static final Pattern QR = Pattern.compile("SJ1\\.[\\w-]+\\.[\\w-]+");

    private LogMasker() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String out = JWT.matcher(message).replaceAll("***jwt");
        out = QR.matcher(out).replaceAll("SJ1.***");
        return KEY_VALUE.matcher(out).replaceAll("$1$2$3***");
    }
}
