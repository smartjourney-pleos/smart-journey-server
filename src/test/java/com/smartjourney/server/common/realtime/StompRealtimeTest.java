package com.smartjourney.server.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjourney.server.common.security.AuthHeaders;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 1-4 · 2-8 실시간: 실제 포트에 STOMP 로 붙어 이벤트가 2초 안에 오는지, 남의 토픽은 막히는지.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompRealtimeTest {

    private static final long STORE_A = 1L;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper om;

    WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(
                List.of(new ByteArrayMessageConverter(), new MappingJackson2MessageConverter(om))));
        post("/api/v1/pos/demo/reset", null, Map.of("Authorization", "Bearer " + posToken()));
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    @Test
    @DisplayName("P0-3 접수 → POS 에 WAITING_CREATED 2초 안 / 차량 매장 토픽에 QUEUE_UPDATED")
    void waitingCreatedReachesPos() throws Exception {
        String jwt = posToken();
        StompSession pos = connect(Map.of(AuthHeaders.AUTHORIZATION, "Bearer " + jwt));
        BlockingQueue<JsonNode> posEvents = subscribe(pos, "/topic/pos/" + STORE_A);

        String dev = newDevice();
        StompSession car = connect(Map.of(AuthHeaders.DEVICE_TOKEN, dev));
        BlockingQueue<JsonNode> queueEvents = subscribe(car, "/topic/stores/" + STORE_A + "/queue");

        long journeyId = startJourney(dev);
        long t0 = System.nanoTime();
        JsonNode waiting = register(dev, journeyId);

        JsonNode created = poll(posEvents, "WAITING_CREATED");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertThat(created.get("payload").get("waitingId").asLong()).isEqualTo(waiting.get("waitingId").asLong());
        assertThat(created.get("payload").get("vehicleVerified").asBoolean()).isTrue();
        assertThat(created.get("eventId").asText()).isNotBlank();
        assertThat(elapsedMs).isLessThan(2000);

        JsonNode queue = poll(queueEvents, "QUEUE_UPDATED");
        assertThat(queue.get("payload").get("aheadTeams").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("T2 데모 +3 → 차량 매장 토픽 QUEUE_UPDATED 1회 / 내 웨이팅 토픽 CALLED + QR")
    void demoPanelAndCall() throws Exception {
        String jwt = posToken();
        String dev = newDevice();
        long journeyId = startJourney(dev);
        long waitingId = register(dev, journeyId).get("waitingId").asLong();

        StompSession car = connect(Map.of(AuthHeaders.DEVICE_TOKEN, dev));
        BlockingQueue<JsonNode> queueEvents = subscribe(car, "/topic/stores/" + STORE_A + "/queue");
        BlockingQueue<JsonNode> mine = subscribe(car, "/topic/waitings/" + waitingId);

        post("/api/v1/pos/demo/walk-ins", "{\"count\":3,\"partySize\":2}", Map.of("Authorization", "Bearer " + jwt));
        JsonNode updated = poll(queueEvents, "QUEUE_UPDATED");
        assertThat(updated.get("payload").get("aheadTeams").asInt()).isEqualTo(4);
        assertThat(updated.get("payload").get("estimatedWaitMin").asInt()).isEqualTo(20);
        Thread.sleep(300);
        assertThat(queueEvents.stream().filter(e -> "QUEUE_UPDATED".equals(e.get("type").asText())))
                .as("데모 +3 은 QUEUE_UPDATED 한 번만").isEmpty();

        post("/api/v1/pos/waitings/" + waitingId + "/call", null, Map.of("Authorization", "Bearer " + jwt));
        JsonNode called = poll(mine, "CALLED");
        assertThat(called.get("payload").get("qrPayload").asText()).startsWith("SJ1.");
        assertThat(called.get("payload").get("ticketNo").asInt()).isPositive();
    }

    @Test
    @DisplayName("2-8 남의 웨이팅 토픽 · 남의 매장 POS 토픽 구독 → 세션 끊김 + 이벤트 안 샘, 토큰 없는 CONNECT 거부")
    void subscriptionAuthorization() throws Exception {
        String jwt = posToken();
        String owner = newDevice();
        long waitingId = register(owner, startJourney(owner)).get("waitingId").asLong();

        // 남의 웨이팅 토픽
        BlockingQueue<String> signals = new LinkedBlockingQueue<>();
        StompSession intruder = connect(Map.of(AuthHeaders.DEVICE_TOKEN, newDevice()), signals);
        BlockingQueue<JsonNode> leaked = new LinkedBlockingQueue<>();
        intruder.subscribe("/topic/waitings/" + waitingId, frameHandler(leaked));
        assertRejected(intruder, signals);

        // 차량이 POS 토픽
        BlockingQueue<String> posSignals = new LinkedBlockingQueue<>();
        StompSession car = connect(Map.of(AuthHeaders.DEVICE_TOKEN, newDevice()), posSignals);
        BlockingQueue<JsonNode> posLeaked = new LinkedBlockingQueue<>();
        car.subscribe("/topic/pos/" + STORE_A, frameHandler(posLeaked));
        assertRejected(car, posSignals);

        // 그 뒤 실제 이벤트가 나가도 새지 않는다
        post("/api/v1/pos/waitings/" + waitingId + "/call", null, Map.of("Authorization", "Bearer " + jwt));
        Thread.sleep(400);
        assertThat(leaked).isEmpty();
        assertThat(posLeaked).isEmpty();

        // 토큰 없는 CONNECT
        BlockingQueue<String> noAuth = new LinkedBlockingQueue<>();
        try {
            StompSession anonymous = connect(Map.of(), noAuth);
            assertRejected(anonymous, noAuth);
        } catch (Exception rejectedOnConnect) {
            // CONNECTED 를 못 받고 끝남 = 거부
        }
    }

    /** ERROR 프레임(FORBIDDEN/UNAUTHORIZED) 또는 연결 종료 신호가 오고, 세션이 끊겨야 한다 */
    private static void assertRejected(StompSession session, BlockingQueue<String> signals) throws InterruptedException {
        String first = signals.poll(3, TimeUnit.SECONDS);
        assertThat(first).as("거부 신호").isNotNull();
        assertThat(first).matches(sig -> sig.contains("FORBIDDEN") || sig.contains("UNAUTHORIZED")
                || sig.startsWith("transport"), "ERROR 프레임 또는 연결 종료");
        long deadline = System.currentTimeMillis() + 3000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).as("세션 끊김").isFalse();
    }

    // ------------------------------------------------------------------

    private StompSession connect(Map<String, String> headers) throws Exception {
        return connect(headers, new LinkedBlockingQueue<>());
    }

    private StompSession connect(Map<String, String> headers, BlockingQueue<String> errors) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        headers.forEach(connectHeaders::add);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders h) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders h, Object payload) {
                        errors.add(String.valueOf(h.getFirst("message")));
                    }

                    @Override
                    public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                        errors.add("exception: " + ex.getMessage());
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable ex) {
                        errors.add("transport: " + ex.getMessage());
                    }
                }).get(3, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, frameHandler(queue));
        Thread.sleep(150);   // SUBSCRIBE 가 브로커에 등록될 시간
        return queue;
    }

    private StompFrameHandler frameHandler(BlockingQueue<JsonNode> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        };
    }

    /** type 이 맞는 이벤트가 올 때까지 (다른 이벤트는 건너뜀). 2초 넘으면 실패 */
    private JsonNode poll(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode e = queue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (e != null && type.equals(e.get("type").asText())) {
                return e;
            }
        }
        throw new AssertionError(type + " 이벤트를 2초 안에 받지 못함");
    }

    private String posToken() {
        return post("/api/v1/pos/login", "{\"storeId\":1,\"pin\":\"1234\"}", Map.of()).get("accessToken").asText();
    }

    private String newDevice() {
        return post("/api/v1/devices", "{\"consentVersion\":\"2026-11-v1\"}", Map.of()).get("deviceToken").asText();
    }

    private long startJourney(String dev) {
        return post("/api/v1/journeys", "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[1]}",
                Map.of(AuthHeaders.DEVICE_TOKEN, dev)).get("journeyId").asLong();
    }

    private JsonNode register(String dev, long journeyId) {
        return post("/api/v1/waitings",
                "{\"journeyId\":" + journeyId + ",\"storeId\":1,\"partySize\":2,\"approvalMethod\":\"TOUCH\"}",
                Map.of(AuthHeaders.DEVICE_TOKEN, dev, AuthHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString()));
    }

    private JsonNode post(String path, String body, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(h::add);
        ResponseEntity<String> res = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as(path + " → " + res.getBody()).isTrue();
        try {
            return om.readTree(res.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
