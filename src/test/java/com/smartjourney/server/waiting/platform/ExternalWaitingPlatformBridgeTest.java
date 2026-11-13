package com.smartjourney.server.waiting.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartjourney.server.waiting.domain.WaitingChannel;
import com.smartjourney.server.waiting.domain.WaitingEventType;
import com.smartjourney.server.waiting.domain.WaitingStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalWaitingPlatformBridgeTest {

    static final class Recording implements ExternalWaitingPlatform {
        final List<WaitingLifecycleEvent> received = new ArrayList<>();

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public void publish(WaitingLifecycleEvent event) {
            received.add(event);
        }
    }

    static final class Broken implements ExternalWaitingPlatform {
        @Override
        public String name() {
            return "broken";
        }

        @Override
        public void publish(WaitingLifecycleEvent event) {
            throw new IllegalStateException("partner down");
        }
    }

    @Test
    @DisplayName("한 어댑터가 실패해도 다른 어댑터는 받고, 예외는 밖으로 안 나간다")
    void isolatesFailures() {
        Recording recording = new Recording();
        ExternalWaitingPlatformBridge bridge = new ExternalWaitingPlatformBridge(List.of(new Broken(), recording));
        WaitingLifecycleEvent event = new WaitingLifecycleEvent(1L, 2L, WaitingChannel.VEHICLE,
                WaitingEventType.REGISTERED, WaitingStatus.REGISTERED, 4, 7);

        bridge.onLifecycle(event);

        assertThat(recording.received).containsExactly(event);
    }

    @Test
    @DisplayName("어댑터가 없으면 아무 일도 안 한다")
    void noAdapters() {
        new ExternalWaitingPlatformBridge(List.of()).onLifecycle(new WaitingLifecycleEvent(1L, 1L,
                WaitingChannel.WALK_IN, WaitingEventType.CANCELLED, WaitingStatus.CANCELLED, 2, 1));
    }
}
