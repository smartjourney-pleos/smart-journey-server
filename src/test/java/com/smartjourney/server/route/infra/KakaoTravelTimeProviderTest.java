package com.smartjourney.server.route.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartjourney.server.common.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTravelTimeProviderTest {

    private static AppProperties props(String key) {
        return new AppProperties(null, null, null, null, null, new AppProperties.Route("kakao", key), null);
    }

    @Test
    @DisplayName("origin/destination 은 경도,위도 순 · KakaoAK 헤더 · duration(초) → 분 올림 · 같은 구간은 캐시")
    void parsesDuration() {
        RestClient.Builder builder = RestClient.builder().baseUrl(KakaoTravelTimeProvider.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoTravelTimeProvider provider = new KakaoTravelTimeProvider(props("test-key"), builder.build());

        server.expect(requestTo(org.hamcrest.Matchers.startsWith(KakaoTravelTimeProvider.BASE_URL + "/v1/directions")))
                .andExpect(queryParam("origin", "126.733500,37.340100"))
                .andExpect(queryParam("destination", "126.738700,37.344600"))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess("""
                        {"routes":[{"result_code":0,"result_msg":"길찾기 성공",
                                    "summary":{"distance":1200,"duration":421}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.minutes(37.3401, 126.7335, 37.3446, 126.7387)).isEqualTo(8);
        assertThat(provider.minutes(37.3401, 126.7335, 37.3446, 126.7387)).as("캐시").isEqualTo(8);
        server.verify();
    }

    @Test
    @DisplayName("경로 없음(result_code≠0)·서버 오류·키 없음 → 예외 (RouteService 가 입력 순서로 폴백)")
    void failures() {
        RestClient.Builder builder = RestClient.builder().baseUrl(KakaoTravelTimeProvider.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoTravelTimeProvider provider = new KakaoTravelTimeProvider(props("k"), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(KakaoTravelTimeProvider.BASE_URL)))
                .andRespond(withSuccess("{\"routes\":[{\"result_code\":104,\"result_msg\":\"출발지와 도착지가 너무 가까움\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(KakaoTravelTimeProvider.BASE_URL)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.minutes(1, 1, 1.0001, 1.0001)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.minutes(2, 2, 3, 3)).isInstanceOf(RuntimeException.class);

        KakaoTravelTimeProvider noKey = new KakaoTravelTimeProvider(props(""), RestClient.create());
        assertThat(noKey.isConfigured()).isFalse();
        assertThatThrownBy(() -> noKey.minutes(1, 1, 2, 2)).isInstanceOf(IllegalStateException.class);
    }
}
