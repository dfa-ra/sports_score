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
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Address the limiter counts.
     *
     * <p>Public traffic is Client → Caddy → nginx → this process. Caddy appends
     * the TCP peer it accepted. nginx then appends Caddy with
     * {@code $proxy_add_x_forwarded_for}, so the header is
     * {@code [client spoof…], <client Caddy saw>, <Caddy>}. The leftmost hop is
     * the one a client can forge and must not be the bucket key. {@code X-Real-IP}
     * is nginx's {@code $remote_addr}, which is Caddy, so it is the proxy address.
     *
     * <p>Forwarded headers are read only when the immediate peer is a private or
     * loopback address (nginx on the compose network; the backend port is not
     * published). The rightmost hop is whoever nginx appended. When that hop is
     * itself a private proxy (Caddy), the client is the hop immediately to its
     * left — including a private client. Anything further left is ignored. When
     * the rightmost hop is public, nginx appended the real client (no Caddy hop)
     * and that address is used. A direct caller is keyed by {@code remoteAddr};
     * a client-supplied {@code X-Forwarded-For} on that connection is ignored.
     * {@code server.forward-headers-strategy} stays off: the framework default
     * skips every private hop and would treat a spoofed public address as the
     * client when the real one is also private.
     */
    static String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            remote = "unknown";
        } else {
            remote = remote.trim();
        }
        if (!trustedProxy(remote)) {
            return remote;
        }
        List<String> hops = forwardedHops(request.getHeader("X-Forwarded-For"));
        if (hops.isEmpty()) {
            return remote;
        }
        String rightmost = hops.get(hops.size() - 1);
        if (trustedProxy(rightmost) && hops.size() >= 2) {
            return hops.get(hops.size() - 2);
        }
        return rightmost;
    }

    private static List<String> forwardedHops(String header) {
        List<String> hops = new ArrayList<>();
        if (header == null || header.isBlank()) {
            return hops;
        }
        for (String part : header.split(",")) {
            String hop = part.trim();
            if (!hop.isEmpty()) {
                hops.add(hop);
            }
        }
        return hops;
    }

    static boolean trustedProxy(String address) {
        if (address == null) {
            return false;
        }
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        if ("::1".equals(value) || value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe80:")) {
            return true;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return false;
            }
        }
        int first = octets[0];
        int second = octets[1];
        if (first == 10 || first == 127) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        return first == 172 && second >= 16 && second <= 31;
    }
}
