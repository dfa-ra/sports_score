package com.studentleague.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        RateLimit rateLimit,
        Redis redis,
        LocalStorage localStorage
) {
    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, long accessExpirationMs, long refreshExpirationMs) {
    }

    public record RateLimit(int authRequestsPerMinute) {
    }

    public record Redis(boolean enabled) {
    }

    /** Локальное хранение файлов на диске сервера. */
    public record LocalStorage(
            String rootDir,
            String publicBaseUrl
    ) {
    }
}
