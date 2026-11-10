package com.smartjourney.server.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간은 항상 Clock 빈으로 얻는다. (골든타임·타임아웃·영업일 계산을 테스트에서 고정하기 위함)
 */
@Configuration
public class ClockConfig {

    /** 영업일(번호표 리셋) 기준 시간대 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
