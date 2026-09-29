package com.studentleague.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        RateLimit rateLimit,
        Redis redis,
        LocalStorage localStorage,
        Admin admin,
        Auth auth,
        DemoData demoData,
        Google google,
        String publicUrl,
        Mail mail
) {
    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, long accessExpirationMs, long refreshExpirationMs) {
    }

    public record RateLimit(int authRequestsPerMinute) {
    }

    public record Redis(boolean enabled) {
    }

    public record LocalStorage(
            String rootDir,
            String publicBaseUrl
    ) {
    }

    /** Единственный админ, создаётся при старте из .env */
    public record Admin(
            String email,
            String password
    ) {
    }

    public record Auth(boolean autoApproveRoles) {
    }

    /** One-shot campus league when the database has no teams yet. */
    public record DemoData(boolean enabled) {
    }

    /** Comma-separated OAuth client ids. Empty disables Google sign-in. */
    public record Google(String clientId) {
    }

    /** SMTP for password reset. Blank host means do not send mail. */
    public record Mail(
            String host,
            int port,
            String username,
            String password,
            String from,
            boolean starttls
    ) {
    }
}
