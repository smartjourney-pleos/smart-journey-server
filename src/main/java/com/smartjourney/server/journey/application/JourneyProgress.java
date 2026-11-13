package com.smartjourney.server.journey.application;

import com.smartjourney.server.journey.domain.Journey;
import com.smartjourney.server.journey.domain.JourneyRepository;
import com.smartjourney.server.waiting.domain.Waiting;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 웨이팅이 끝났을 때(착석·노쇼·취소) 코스 쪽을 맞춘다.
 * 그 매장 정차를 DONE 으로, 남은 매장이 없으면 여정도 끝낸다 ("마지막 매장 착석·취소 시 종료").
 */
@Component
@RequiredArgsConstructor
public class JourneyProgress {

    private final JourneyRepository journeyRepository;
    private final Clock clock;

    public void waitingClosed(Waiting waiting) {
        if (waiting.getJourneyId() == null) {
            return;
        }
        journeyRepository.findById(waiting.getJourneyId()).ifPresent(journey -> close(journey, waiting.getId()));
    }

    private void close(Journey journey, long waitingId) {
        journey.stopForWaiting(waitingId).ifPresent(stop -> stop.markDone());
        if (journey.isActive() && !journey.hasPendingStops()) {
            journey.end(clock.instant());
        }
    }
}
