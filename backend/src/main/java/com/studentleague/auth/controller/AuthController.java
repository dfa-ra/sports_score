package com.studentleague.auth.controller;

import com.studentleague.auth.dto.AuthResponse;
import com.studentleague.auth.dto.ForgotPasswordRequest;
import com.studentleague.auth.dto.GoogleLoginRequest;
import com.studentleague.auth.dto.LoginRequest;
import com.studentleague.auth.dto.LogoutRequest;
import com.studentleague.auth.dto.RefreshRequest;
import com.studentleague.auth.dto.RegisterRequest;
import com.studentleague.auth.dto.ResetPasswordRequest;
import com.studentleague.auth.dto.UserResponse;
import com.studentleague.auth.google.GoogleTokenVerifier;
import com.studentleague.auth.service.AuthRateLimiter;
import com.studentleague.auth.service.AuthService;
import com.studentleague.auth.service.PasswordResetService;
import com.studentleague.auth.service.RegistrationUploadTicketService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.storage.ImageUploads;
import com.studentleague.storage.StorageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final StorageService storageService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PasswordResetService passwordResetService;
    private final RegistrationUploadTicketService registrationUploadTickets;

    public AuthController(
            AuthService authService,
            AuthRateLimiter authRateLimiter,
            StorageService storageService,
            GoogleTokenVerifier googleTokenVerifier,
            PasswordResetService passwordResetService,
            RegistrationUploadTicketService registrationUploadTickets
    ) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.storageService = storageService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordResetService = passwordResetService;
        this.registrationUploadTickets = registrationUploadTickets;
    }

    @PostMapping("/register")
    @Operation(summary = "Регистрация: ФИО, почта, роль. Игрок/капитан/судья прикладывают фото")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.check(httpRequest, request.email());
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/photo-ticket")
    @Operation(summary = "Одноразовый код загрузки фото до создания аккаунта")
    public Map<String, String> registrationPhotoTicket(HttpServletRequest httpRequest) {
        authRateLimiter.checkPhotoTicket(httpRequest, currentUserId());
        return Map.of("token", registrationUploadTickets.issue());
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить фото для регистрации (игрок / капитан / судья)")
    public Map<String, String> uploadRegistrationPhoto(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "token", required = false) String token,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.check(httpRequest);
        if (principal == null) {
            String presented = token;
            if (presented == null || presented.isBlank()) {
                presented = httpRequest.getHeader(RegistrationUploadTicketService.HEADER);
            }
            registrationUploadTickets.consume(presented);
        }
        ImageUploads.requireRasterImage(file);
        return Map.of("url", storageService.store("registration", file));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive access/refresh tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        authRateLimiter.check(httpRequest, request.email());
        AuthService.AuthTokens tokens = authService.login(request.email(), request.password());
        return toResponse(tokens);
    }

    @PostMapping("/google")
    @Operation(summary = "Войти через Google ID token. Новый аккаунт создаётся как FAN")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        authRateLimiter.check(httpRequest);
        return toResponse(authService.loginWithGoogle(googleTokenVerifier.verify(request.idToken())));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Запросить ссылку для сброса пароля. Ответ одинаковый, есть аккаунт или нет")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.checkForgotPassword(httpRequest, request.email());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password-email")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ссылка для смены пароля на почту текущего аккаунта. Ответ всегда 204")
    public ResponseEntity<Void> changePasswordEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.check(httpRequest, principal == null ? null : principal.getId().toString());
        passwordResetService.requestResetForCurrentUser(principal == null ? null : principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Задать новый пароль по одноразовой ссылке")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.check(httpRequest, request.token());
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        authRateLimiter.check(httpRequest);
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Current authenticated user")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.getId());
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.getId().toString();
    }

    private static AuthResponse toResponse(AuthService.AuthTokens tokens) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.expiresInSeconds(),
                tokens.user()
        );
    }
}
