package com.smartjourney.server.common.config;

import com.smartjourney.server.common.security.AuthHeaders;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html — 심사 자료·팀 내 API 계약서로 사용.
 */
@Configuration
public class OpenApiConfig {

    public static final String DEVICE_SCHEME = "deviceToken";
    public static final String POS_SCHEME = "posJwt";

    @Bean
    public OpenAPI smartJourneyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Journey Server API")
                        .version("v1")
                        .description("차량 앱(Pleos Connect) ↔ 중계 서버 ↔ 매장 웹 POS. "
                                + "명령은 REST, 상태 변화는 STOMP(/ws) 구독."))
                .components(new Components()
                        .addSecuritySchemes(DEVICE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AuthHeaders.DEVICE_TOKEN))
                        .addSecuritySchemes(POS_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
