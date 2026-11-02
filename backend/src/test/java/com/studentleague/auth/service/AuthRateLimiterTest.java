package com.studentleague.auth.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    @Test
    void blocksIpAndAccountAfterTheConfiguredAttempts() {
        AuthRateLimiter limiter = limiter(3, null);

        limiter.check(request("10.0.0.1"), "a@example.com");
        limiter.check(request("10.0.0.2"), "a@example.com");
        limiter.check(request("10.0.0.3"), "A@Example.com");

        assertThatThrownBy(() -> limiter.check(request("10.0.0.4"), "a@example.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getMessage()).isEqualTo(AuthRateLimiter.TOO_MANY);
                });

        limiter.check(request("10.1.0.1"), "other@example.com");
        limiter.check(request("10.1.0.1"), "other@example.com");
        limiter.check(request("10.1.0.1"), "other@example.com");
        assertThatThrownBy(() -> limiter.check(request("10.1.0.1"), "fresh@example.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void usesRedisCountersWhenTheTemplateIsAvailable() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L, 1L, 2L);
        when(redis.getExpire(anyString())).thenReturn(30L);
        AuthRateLimiter limiter = limiter(1, redis);

        limiter.check(request("10.2.0.1"), "one@example.com");
        assertThatThrownBy(() -> limiter.check(request("10.2.0.2"), "one@example.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fallsBackToMemoryWhenRedisIsDown() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        AuthRateLimiter limiter = limiter(1, redis);

        limiter.check(request("10.3.0.1"));
        assertThatThrownBy(() -> limiter.check(request("10.3.0.1")))
                .isInstanceOf(ApiException.class);
    }

    private static AuthRateLimiter limiter(int capacity, StringRedisTemplate redis) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-algorithms-123456", 60_000, 3_600_000),
                new AppProperties.RateLimit(capacity),
                new AppProperties.Redis(redis != null),
                new AppProperties.LocalStorage("./data/uploads-test", "/media"),
                new AppProperties.Admin("", ""),
                new AppProperties.Auth(false),
                new AppProperties.DemoData(false),
                new AppProperties.Google(""),
                "http://localhost:5173",
                new AppProperties.Mail("", 587, "", "", "", true)
        );
        return new AuthRateLimiter(properties, redis);
    }

    private static MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }
}
