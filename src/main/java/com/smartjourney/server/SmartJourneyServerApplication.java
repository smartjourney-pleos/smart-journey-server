package com.smartjourney.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Journey 중계 서버.
 *
 * <pre>
 * 차량 앱(Pleos Connect) ──REST(명령)──▶ 서버 ──STOMP(상태 변화)──▶ 차량 앱 / 매장 웹 POS
 * 매장 웹 POS          ──REST(명령)──▶ 서버
 * </pre>
 *
 * 명령은 REST, 상태 변화 알림은 STOMP 구독. 클라이언트는 재연결 시 REST 로 현재 상태를 다시 받아 맞춘다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartJourneyServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartJourneyServerApplication.class, args);
    }
}
