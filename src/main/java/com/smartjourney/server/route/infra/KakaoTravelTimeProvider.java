package com.smartjourney.server.route.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.route.domain.TravelTimeProvider;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오모빌리티 길찾기 (자동차) — {@code GET /v1/directions?origin=lng,lat&destination=lng,lat}
 * 응답 {@code routes[0].summary.duration}(초)을 분으로 올림한다.
 *
 * <ul>
 *   <li>키는 서버 환경변수 KAKAO_REST_API_KEY 에만 (.env). 앱에 넣지 않는다</li>
 *   <li>타임아웃 2초 — 음성(Gleo 7초) 경로를 막지 않게. 실패하면 RouteService 가 입력 순서로 폴백</li>
 *   <li>매장 좌표는 잘 안 바뀌므로 구간 결과를 메모리에 캐시 (서버 재시작 시 초기화)</li>
 * </ul>
 */
@Component
public class KakaoTravelTimeProvider implements TravelTimeProvider {

    static final String BASE_URL = "https://apis-navi.kakaomobility.com";

    private final AppProperties properties;
    private final RestClient restClient;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    @Autowired
    public KakaoTravelTimeProvider(AppProperties properties, RestClient.Builder builder) {
        this(properties, builder.baseUrl(BASE_URL).requestFactory(timeoutFactory()).build());
    }

    /** 테스트에서 가짜 서버(MockRestServiceServer)에 묶은 RestClient 를 넣을 때 */
    KakaoTravelTimeProvider(AppProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return factory;
    }

    @Override
    public String name() {
        return "kakao";
    }

    public boolean isConfigured() {
        String key = properties.route().kakaoRestApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public int minutes(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isConfigured()) {
            throw new IllegalStateException("KAKAO_REST_API_KEY 없음");
        }
        String origin = coord(fromLng, fromLat);
        String destination = coord(toLng, toLat);
        return cache.computeIfAbsent(origin + ">" + destination, k -> fetch(origin, destination));
    }

    private int fetch(String origin, String destination) {
        JsonNode body = restClient.get()
                .uri(uri -> uri.path("/v1/directions")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("priority", "RECOMMEND")
                        .build())
                .header("Authorization", "KakaoAK " + properties.route().kakaoRestApiKey())
                .retrieve()
                .body(JsonNode.class);
        JsonNode route = body == null ? null : body.path("routes").path(0);
        if (route == null || route.path("result_code").asInt(-1) != 0) {
            throw new IllegalStateException("kakao directions failed: "
                    + (route == null ? "empty" : route.path("result_msg").asText()));
        }
        int seconds = route.path("summary").path("duration").asInt(-1);
        if (seconds < 0) {
            throw new IllegalStateException("kakao directions: no duration");
        }
        return (int) Math.ceil(seconds / 60.0);
    }

    private static String coord(double lng, double lat) {
        return String.format(Locale.ROOT, "%.6f,%.6f", lng, lat);
    }
}
