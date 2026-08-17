package com.studentleague.matches.live;

import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveMatchPublisher {

    public static final String TOPIC_PREFIX = "/topic/matches/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    public LiveMatchPublisher(
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.messagingTemplate = messagingTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
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
                lastEvent
        );
        messagingTemplate.convertAndSend(TOPIC_PREFIX + match.id(), update);
        applicationEventPublisher.publishEvent(update);
    }
}
