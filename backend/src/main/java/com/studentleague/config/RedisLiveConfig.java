package com.studentleague.config;

import com.studentleague.matches.live.RedisLiveMatchBridge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLiveConfig {

    @Bean
    RedisConnectionFactory redisConnectionFactory(@Value("${spring.data.redis.url:redis://localhost:6379}") String redisUrl) {
        URI uri = URI.create(redisUrl);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost() == null ? "localhost" : uri.getHost());
        config.setPort(uri.getPort() <= 0 ? 6379 : uri.getPort());
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String[] parts = uri.getUserInfo().split(":", 2);
            config.setUsername(parts[0].isBlank() ? null : parts[0]);
            config.setPassword(parts[1]);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisLiveMatchBridge bridge
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(bridge, new ChannelTopic(RedisLiveMatchBridge.CHANNEL));
        return container;
    }
}
