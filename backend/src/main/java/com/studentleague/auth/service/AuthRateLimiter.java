package com.studentleague.auth.service;

import com.studentleague.config.AppProperties;
import com.studentleague.common.exception.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;

    public AuthRateLimiter(AppProperties appProperties) {
        this.capacity = Math.max(1, appProperties.rateLimit().authRequestsPerMinute());
    }

    public void check(HttpServletRequest request) {
        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
        if (!bucket.tryConsume(1)) {
            throw ApiException.rateLimited("Too many authentication requests");
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
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
