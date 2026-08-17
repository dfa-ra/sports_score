package com.studentleague.matches.live;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveMatchPublisherTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    ObjectProvider<RedisLiveMatchBridge> redisBridge;

    @Test
    void publishesToMatchTopicWhenRedisDisabled() {
        when(redisBridge.getIfAvailable()).thenReturn(null);
        LiveMatchPublisher publisher = new LiveMatchPublisher(messagingTemplate, applicationEventPublisher, redisBridge);

        UUID matchId = UUID.randomUUID();
        MatchResponse match = new MatchResponse(
                matchId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), Instant.now(), null, MatchStatus.LIVE, 2, 1, 90, 1
        );

        publisher.publishMatchUpdate(match, null, "MATCH_EVENT");

        ArgumentCaptor<LiveMatchUpdate> captor = ArgumentCaptor.forClass(LiveMatchUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/matches/" + matchId), captor.capture());
        verify(applicationEventPublisher).publishEvent(captor.getValue());
        assertThat(captor.getValue().homeScore()).isEqualTo(2);
        assertThat(captor.getValue().type()).isEqualTo("MATCH_EVENT");
    }
}
