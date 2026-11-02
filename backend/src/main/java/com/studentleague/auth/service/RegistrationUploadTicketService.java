package com.studentleague.auth.service;

import com.studentleague.common.exception.ApiException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time upload grant for the registration form, which sends the photo
 * before the account exists. A missing or reused code is rejected with 401.
 */
@Service
public class RegistrationUploadTicketService {

    public static final String HEADER = "X-Registration-Upload";

    static final Duration TTL = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> expiresAt = new ConcurrentHashMap<>();

    public String issue() {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        expiresAt.put(token, Instant.now().plus(TTL));
        return token;
    }

    public void consume(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        Instant deadline = expiresAt.remove(token.trim());
        if (deadline == null || !Instant.now().isBefore(deadline)) {
            throw unauthorized();
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        expiresAt.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
    }

    private static ApiException unauthorized() {
        return ApiException.unauthorized("Нужен одноразовый код загрузки фото");
    }
}
