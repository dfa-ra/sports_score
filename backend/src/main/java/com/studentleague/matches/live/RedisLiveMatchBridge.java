package com.studentleague.matches.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLiveMatchBridge implements MessageListener {

    public static final String CHANNEL = "studentleague:match-live";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisLiveMatchBridge(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(LiveMatchUpdate update) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(update));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish live match update to Redis", ex);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            LiveMatchUpdate update = objectMapper.readValue(message.getBody(), LiveMatchUpdate.class);
            // Fan-out to local STOMP subscribers (multi-instance safe when each node listens).
            messagingTemplate.convertAndSend(LiveMatchPublisher.TOPIC_PREFIX + update.matchId(), update);
        } catch (Exception ignored) {
            // drop malformed payloads
        }
    }
}
