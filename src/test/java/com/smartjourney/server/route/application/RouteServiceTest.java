package com.smartjourney.server.route.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartjourney.server.common.config.AppProperties;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteRequest;
import com.smartjourney.server.route.api.RouteDtos.OptimizeRouteResponse;
import com.smartjourney.server.route.domain.TravelTimeProvider;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouteServiceTest {

    /** 위도 값을 "위치" 로 보고 |a-b| × 10 분 걸린다고 가정 (1=0.0, 2=1.0, 3=2.0) */
    static final class LineProvider implements TravelTimeProvider {
        @Override
        public String name() {
            return "kakao";
        }

        @Override
        public int minutes(double fromLat, double fromLng, double toLat, double toLng) {
            return (int) Math.round(Math.abs(fromLat - toLat) * 10);
        }
    }

    static final class BrokenProvider implements TravelTimeProvider {
        @Override
        public String name() {
            return "kakao";
        }

        @Override
        public int minutes(double fromLat, double fromLng, double toLat, double toLng) {
            throw new IllegalStateException("timeout");
        }
    }

    private static Store store(long id, double lat) {
        Store s = mock(Store.class);
        when(s.getId()).thenReturn(id);
        when(s.getLatitude()).thenReturn(lat);
        when(s.getLongitude()).thenReturn(127.0);
        return s;
    }

    private static AppProperties props(String provider) {
        return new AppProperties(null, null, null, null, null, new AppProperties.Route(provider, "key"), null);
    }

    private static StoreRepository repo() {
        List<Store> stores = List.of(store(1, 0.0), store(2, 1.0), store(3, 2.0));
        StoreRepository repo = mock(StoreRepository.class);
        when(repo.findAllById(any())).thenReturn(stores);
        return repo;
    }

    @Test
    @DisplayName("kakao: 3 → 1 → 2 로 고른 걸 이동 시간이 짧은 순서로 바꾼다 (첫 구간은 앱이 준 분)")
    void optimizes() {
        RouteService service = new RouteService(props("kakao"), repo(), List.of(new LineProvider()));
        OptimizeRouteResponse r = service.optimize(
                new OptimizeRouteRequest(List.of(3L, 1L, 2L), Map.of(1L, 5, 2L, 10, 3L, 15)));
        assertThat(r.optimized()).isTrue();
        assertThat(r.provider()).isEqualTo("kakao");
        assertThat(r.orderedStoreIds()).containsExactly(1L, 2L, 3L);
        assertThat(r.legsMin()).containsExactly(5, 10, 10);
        assertThat(r.totalTravelMin()).isEqualTo(25);
    }

    @Test
    @DisplayName("외부 API 실패 → 입력 순서 그대로 (optimized=false)")
    void fallsBack() {
        RouteService service = new RouteService(props("kakao"), repo(), List.of(new BrokenProvider()));
        OptimizeRouteResponse r = service.optimize(new OptimizeRouteRequest(List.of(3L, 1L, 2L), null));
        assertThat(r.optimized()).isFalse();
        assertThat(r.orderedStoreIds()).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("provider=none → 입력 순서 그대로")
    void none() {
        RouteService service = new RouteService(props("none"), repo(), List.of(new LineProvider()));
        assertThat(service.optimize(new OptimizeRouteRequest(List.of(3L, 1L, 2L), null)).orderedStoreIds())
                .containsExactly(3L, 1L, 2L);
    }
}
