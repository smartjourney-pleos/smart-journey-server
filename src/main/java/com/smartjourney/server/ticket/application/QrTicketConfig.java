package com.smartjourney.server.ticket.application;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.ticket.domain.QrTicketCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QrTicketConfig {

    @Bean
    public QrTicketCodec qrTicketCodec(AppProperties properties) {
        return new QrTicketCodec(properties.qr().secret());
    }
}
