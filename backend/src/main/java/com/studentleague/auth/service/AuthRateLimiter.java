package com.studentleague.auth.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimiter {

    static final String TOO_MANY = "Слишком много попыток. Подождите минуту";

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final StringRedisTemplate redis;

    @Autowired
    public AuthRateLimiter(AppProperties appProperties, ObjectProvider<StringRedisTemplate> redis) {
        this(appProperties, redis.getIfAvailable());
    }

    AuthRateLimiter(AppProperties appProperties, StringRedisTemplate redis) {
        this.capacity = Math.max(1, appProperties.rateLimit().authRequestsPerMinute());
        this.redis = redis;
    }

    public void check(HttpServletRequest request) {
        check(request, null);
    }

    public void check(HttpServletRequest request, String account) {
        consume("ip:" + clientKey(request));
        if (account != null && !account.isBlank()) {
            consume("acct:" + account.trim().toLowerCase(Locale.ROOT));
        }
    }

    private void consume(String key) {
        if (redis != null && consumeRedis(key)) {
            return;
        }
        consumeMemory(key);
    }

    private boolean consumeRedis(String key) {
        String redisKey = "auth:rl:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return false;
            }
            Long ttl = redis.getExpire(redisKey);
            if (ttl == null || ttl < 0) {
                redis.expire(redisKey, WINDOW);
            }
            if (count > capacity) {
                throw ApiException.rateLimited(TOO_MANY);
            }
            return true;
        } catch (DataAccessException ex) {
            log.warn("Redis rate limit failed ({}), using memory", ex.getClass().getSimpleName());
            return false;
        }
    }

    private void consumeMemory(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
        if (!bucket.tryConsume(1)) {
            throw ApiException.rateLimited(TOO_MANY);
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, WINDOW)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
