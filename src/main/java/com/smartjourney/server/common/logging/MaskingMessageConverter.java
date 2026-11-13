package com.smartjourney.server.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * logback {@code %masked} — 로그 메시지를 {@link LogMasker} 로 가린 뒤 출력. (logback-spring.xml)
 */
public class MaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(super.convert(event));
    }
}
