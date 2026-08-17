package com.studentleague.matches.live;

import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveMatchPublisher {

    public static final String TOPIC_PREFIX = "/topic/matches/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectProvider<RedisLiveMatchBridge> redisBridge;

    public LiveMatchPublisher(
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectProvider<RedisLiveMatchBridge> redisBridge
    ) {
        this.messagingTemplate = messagingTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
        this.redisBridge = redisBridge;
    }

    public void publishMatchUpdate(MatchResponse match, MatchEventResponse lastEvent, String type) {
        LiveMatchUpdate update = new LiveMatchUpdate(
                type,
                match.id(),
                match.status(),
                match.homeScore(),
                match.awayScore(),
                match.gameTimeSeconds(),
                match.period(),
                match.periodCount(),
                match.periodLengthSeconds(),
                match.clockRunningSince(),
                match.sportCode(),
                lastEvent
        );

        RedisLiveMatchBridge bridge = redisBridge.getIfAvailable();
        if (bridge != null) {
            // Multi-instance: publish to Redis; each node fans out to local STOMP clients.
            bridge.publish(update);
        } else {
            // Single-instance: publish directly to local broker.
            messagingTemplate.convertAndSend(TOPIC_PREFIX + match.id(), update);
        }
        applicationEventPublisher.publishEvent(update);
    }
}
