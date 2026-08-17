package com.studentleague.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        RateLimit rateLimit,
        S3 s3
) {
    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, long accessExpirationMs, long refreshExpirationMs) {
    }

    public record RateLimit(int authRequestsPerMinute) {
    }

    public record S3(String endpoint, String accessKey, String secretKey, String bucket) {
    }
}
