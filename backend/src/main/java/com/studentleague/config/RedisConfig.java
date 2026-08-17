package com.studentleague.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Redis is available for cache / pub-sub / rate-limit clustering in later phases.
 * Local rate limiting currently uses in-memory Bucket4j so auth works without Redis.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@EnableRedisRepositories
public class RedisConfig {
}
