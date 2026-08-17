package com.studentleague.auth.service;

import com.studentleague.auth.entity.RefreshToken;
import com.studentleague.auth.repository.RefreshTokenRepository;
import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.security.JwtService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    @Transactional
    public UserResponse register(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.FAN);
        user.setEnabled(true);
        userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (!existing.isActive()) {
            throw ApiException.unauthorized("Refresh token expired or revoked");
        }
        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("User disabled");
        }

        RefreshToken replacement = createRefreshTokenEntity(user.getId());
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(replacement.getId());
        refreshTokenRepository.save(existing);
        refreshTokenRepository.save(replacement);

        UserPrincipal principal = UserPrincipal.from(user);
        String accessToken = jwtService.createAccessToken(principal);
        return new AuthTokens(accessToken, replacement.getRawToken(), jwtService.getAccessExpirationMs() / 1000, toUserResponse(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toUserResponse(user);
    }

    private AuthTokens issueTokens(User user) {
        RefreshToken refreshToken = createRefreshTokenEntity(user.getId());
        refreshTokenRepository.save(refreshToken);
        UserPrincipal principal = UserPrincipal.from(user);
        String accessToken = jwtService.createAccessToken(principal);
        return new AuthTokens(accessToken, refreshToken.getRawToken(), jwtService.getAccessExpirationMs() / 1000, toUserResponse(user));
    }

    private RefreshToken createRefreshTokenEntity(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(appProperties.jwt().refreshExpirationMs()));
        token.setRawToken(rawToken);
        return token;
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled());
    }

    public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
    }
}
