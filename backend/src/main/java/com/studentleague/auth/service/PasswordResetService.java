package com.studentleague.auth.service;

import com.studentleague.auth.entity.PasswordResetToken;
import com.studentleague.auth.entity.RefreshToken;
import com.studentleague.auth.mail.PasswordResetMailer;
import com.studentleague.auth.repository.PasswordResetTokenRepository;
import com.studentleague.auth.repository.RefreshTokenRepository;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer passwordResetMailer;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetMailer passwordResetMailer,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetMailer = passwordResetMailer;
        this.appProperties = appProperties;
    }

    @Transactional
    public void requestResetForCurrentUser(UUID userId) {
        if (userId == null) {
            AuthService.hashToken(randomToken());
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            AuthService.hashToken(randomToken());
            return;
        }
        requestReset(user.getEmail());
    }

    @Transactional
    public void requestReset(String email) {
        PublicResetLink.normalizeBase(appProperties.publicUrl());
        String normalized = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null || !user.isEnabled()) {
            AuthService.hashToken(randomToken());
            return;
        }
        invalidateUnused(user.getId());
        String rawToken = randomToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setTokenHash(AuthService.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        passwordResetTokenRepository.save(token);
        String link = PublicResetLink.build(appProperties.publicUrl(), rawToken);
        try {
            passwordResetMailer.sendResetLink(user.getEmail(), link);
        } catch (RuntimeException ex) {
            log.warn("Password reset email was not sent to {}: {}", user.getEmail(), ex.getClass().getSimpleName());
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("Ссылка недействительна или устарела");
        }
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(AuthService.hashToken(rawToken.trim()))
                .orElseThrow(() -> ApiException.badRequest("Ссылка недействительна или устарела"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Ссылка недействительна или устарела");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("Ссылка недействительна или устарела"));
        if (!user.isEnabled()) {
            throw ApiException.badRequest("Ссылка недействительна или устарела");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        Instant now = Instant.now();
        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
        invalidateUnused(user.getId());
        revokeRefreshTokens(user.getId(), now);
    }

    private void invalidateUnused(UUID userId) {
        List<PasswordResetToken> active = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(userId);
        if (active.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (PasswordResetToken token : active) {
            token.setUsedAt(now);
        }
        passwordResetTokenRepository.saveAll(active);
    }

    private void revokeRefreshTokens(UUID userId, Instant now) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (active.isEmpty()) {
            return;
        }
        for (RefreshToken token : active) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(active);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
