package com.studentleague.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Redis repositories when app.redis.enabled is true.
 * Auth rate limits use the same StringRedisTemplate and fall back to memory when it is off.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@EnableRedisRepositories
public class RedisConfig {
}
