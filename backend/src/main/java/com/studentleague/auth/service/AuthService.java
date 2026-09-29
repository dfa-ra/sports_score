package com.studentleague.auth.service;

import com.studentleague.auth.entity.RefreshToken;
import com.studentleague.auth.google.GoogleIdentity;
import com.studentleague.auth.repository.RefreshTokenRepository;
import com.studentleague.auth.dto.RegisterRequest;
import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.JwtService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRoleAssignmentRepository;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
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
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PlayerProfileRepository playerProfileRepository,
            UserRoleAssignmentRepository assignmentRepository,
            RoleService roleService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.assignmentRepository = assignmentRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw ApiException.conflict("Email already registered");
        }

        List<Role> requested = request.resolvedRoles();
        if (requested.isEmpty() || requested.stream().anyMatch(role -> role == Role.ADMIN)) {
            throw ApiException.badRequest("Можно зарегистрироваться как FAN, PLAYER, CAPTAIN или REFEREE");
        }

        if (isReservedAdminEmail(normalizedEmail)) {
            throw ApiException.forbidden("Этот email зарезервирован для администратора");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhotoUrl(blankToNull(request.photoUrl()));
        user.setRole(Role.FAN);
        user.setEnabled(true);
        userRepository.save(user);

        roleService.grantApproved(user, Role.FAN, null);
        for (Role role : requested) {
            if (role != Role.FAN) {
                roleService.requestRole(user, role, request.photoUrl());
            }
        }

        ensurePlayerProfile(user, request.firstName().trim(), request.lastName().trim(), blankToNull(request.photoUrl()));

        return roleService.toUserResponse(user);
    }

    @Transactional
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        String hash = user.getPasswordHash();
        if (!user.isEnabled() || hash == null || hash.isBlank() || !passwordEncoder.matches(rawPassword, hash)) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return issueTokens(user);
    }

    /**
     * Existing Google subject logs in. Same verified email links the subject.
     * Otherwise a FAN account is created. PLAYER/CAPTAIN/REFEREE are not granted.
     */
    @Transactional
    public AuthTokens loginWithGoogle(GoogleIdentity identity) {
        String email = identity.email().trim().toLowerCase();
        String subject = identity.subject().trim();
        User bySub = userRepository.findByGoogleSub(subject).orElse(null);
        User byEmail = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (bySub != null) {
            if (!bySub.isEnabled()) {
                throw ApiException.unauthorized("Аккаунт отключён");
            }
            fillBlankNames(bySub, identity);
            applyGooglePhotoIfMissing(bySub, identity);
            userRepository.save(bySub);
            ensurePlayerProfile(bySub, bySub.getFirstName(), bySub.getLastName(), bySub.getPhotoUrl());
            return issueTokens(bySub);
        }

        if (byEmail != null) {
            if (!byEmail.isEnabled()) {
                throw ApiException.unauthorized("Аккаунт отключён");
            }
            if (byEmail.getGoogleSub() != null && !byEmail.getGoogleSub().equals(subject)) {
                throw ApiException.conflict("Этот email уже связан с другим аккаунтом Google");
            }
            byEmail.setGoogleSub(subject);
            fillBlankNames(byEmail, identity);
            applyGooglePhotoIfMissing(byEmail, identity);
            userRepository.save(byEmail);
            ensurePlayerProfile(byEmail, byEmail.getFirstName(), byEmail.getLastName(), byEmail.getPhotoUrl());
            return issueTokens(byEmail);
        }

        if (isReservedAdminEmail(email)) {
            throw ApiException.forbidden("Этот email зарезервирован для администратора");
        }

        String firstName = cleanName(identity.givenName(), cleanName(emailLocalPart(email), "Болельщик"));
        String lastName = cleanName(identity.familyName(), "—");
        String photo = httpsPicture(identity.pictureUrl());
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setGoogleSub(subject);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhotoUrl(photo);
        user.setRole(Role.FAN);
        user.setEnabled(true);
        userRepository.save(user);
        roleService.grantApproved(user, Role.FAN, null);
        ensurePlayerProfile(user, firstName, lastName, photo);
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

        UserPrincipal principal = UserPrincipal.from(user, assignmentRepository.findByUserId(user.getId()));
        String accessToken = jwtService.createAccessToken(principal);
        return new AuthTokens(accessToken, replacement.getRawToken(), jwtService.getAccessExpirationMs() / 1000, roleService.toUserResponse(user));
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
        return roleService.toUserResponse(user);
    }

    private AuthTokens issueTokens(User user) {
        RefreshToken refreshToken = createRefreshTokenEntity(user.getId());
        refreshTokenRepository.save(refreshToken);
        UserPrincipal principal = UserPrincipal.from(user, assignmentRepository.findByUserId(user.getId()));
        String accessToken = jwtService.createAccessToken(principal);
        return new AuthTokens(accessToken, refreshToken.getRawToken(), jwtService.getAccessExpirationMs() / 1000, roleService.toUserResponse(user));
    }

    private void ensurePlayerProfile(User user, String firstName, String lastName, String photoUrl) {
        PlayerProfile existing = playerProfileRepository.findByUserId(user.getId()).orElse(null);
        if (existing != null) {
            if ((existing.getAvatarUrl() == null || existing.getAvatarUrl().isBlank()) && photoUrl != null && !photoUrl.isBlank()) {
                existing.setAvatarUrl(photoUrl);
                playerProfileRepository.save(existing);
            }
            return;
        }
        String first = cleanName(firstName, "Болельщик");
        String last = cleanName(lastName, "—");
        PlayerProfile profile = new PlayerProfile();
        profile.setUserId(user.getId());
        profile.setFirstName(first);
        profile.setLastName(last);
        profile.setDisplayName(first + " " + last);
        profile.setAvatarUrl(blankToNull(photoUrl));
        playerProfileRepository.save(profile);
    }

    private void fillBlankNames(User user, GoogleIdentity identity) {
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName(cleanName(identity.givenName(), cleanName(emailLocalPart(user.getEmail()), "Болельщик")));
        }
        if (user.getLastName() == null || user.getLastName().isBlank()) {
            user.setLastName(cleanName(identity.familyName(), "—"));
        }
    }

    private void applyGooglePhotoIfMissing(User user, GoogleIdentity identity) {
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isBlank()) {
            return;
        }
        String photo = httpsPicture(identity.pictureUrl());
        if (photo != null) {
            user.setPhotoUrl(photo);
        }
    }

    private boolean isReservedAdminEmail(String email) {
        AppProperties.Admin admin = appProperties.admin();
        return admin != null && admin.email() != null && !admin.email().isBlank()
                && email != null
                && email.equalsIgnoreCase(admin.email().trim());
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String cleanName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100);
    }

    private static String httpsPicture(String picture) {
        if (picture == null) {
            return null;
        }
        String trimmed = picture.trim();
        if (trimmed.length() > 1024 || trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\\') >= 0) {
            return null;
        }
        if (!trimmed.startsWith("https://")) {
            return null;
        }
        return trimmed;
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
    }
}
