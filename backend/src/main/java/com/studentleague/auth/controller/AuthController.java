package com.studentleague.auth.controller;

import com.studentleague.auth.dto.AuthResponse;
import com.studentleague.auth.dto.LoginRequest;
import com.studentleague.auth.dto.LogoutRequest;
import com.studentleague.auth.dto.RefreshRequest;
import com.studentleague.auth.dto.RegisterRequest;
import com.studentleague.auth.dto.UserResponse;
import com.studentleague.auth.service.AuthRateLimiter;
import com.studentleague.auth.service.AuthService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter authRateLimiter) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    @Operation(summary = "Регистрация зрителя (FAN) или игрока (PLAYER)")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.check(httpRequest);
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive access/refresh tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        authRateLimiter.check(httpRequest);
        AuthService.AuthTokens tokens = authService.login(request.email(), request.password());
        return toResponse(tokens);
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
