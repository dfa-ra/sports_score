package com.studentleague.auth;

import com.studentleague.auth.entity.PasswordResetToken;
import com.studentleague.auth.mail.PasswordResetMailer;
import com.studentleague.auth.repository.PasswordResetTokenRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private PasswordResetMailer mailer;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void resetChangesPasswordRevokesRefreshAndRejectsReuse() throws Exception {
        String email = "reset-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        String refresh = loginRefresh(email, "Str0ngPass!");

        String rawToken = requestToken(email);
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(stored.getUsedAt()).isNull();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now().plus(50, ChronoUnit.MINUTES));
        assertThat(passwordResetTokenRepository.findAll())
                .allSatisfy(token -> assertThat(token.getTokenHash()).isNotEqualTo(rawToken));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"short"}
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest());
        assertThat(passwordResetTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow().getUsedAt()).isNull();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(rawToken)))
                .andExpect(status().isNoContent());

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass123", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Str0ngPass!", user.getPasswordHash())).isFalse();
        assertThat(passwordResetTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow().getUsedAt()).isNotNull();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"Another123"}
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPass123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailStillNoContentAndStoresNothing() throws Exception {
        String email = "missing-" + System.nanoTime() + "@example.com";
        long before = passwordResetTokenRepository.count();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());

        assertThat(passwordResetTokenRepository.count()).isEqualTo(before);
        verify(mailer, never()).sendResetLink(eq(email), anyString());
    }

    @Test
    void expiredTokenCannotReset() throws Exception {
        String email = "expired-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        String rawToken = requestToken(email);
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        passwordResetTokenRepository.saveAndFlush(stored);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void newerRequestInvalidatesPreviousToken() throws Exception {
        String email = "rotate-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        String first = requestToken(email);
        String second = requestToken(email);
        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(first)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(second)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePasswordEmailCreatesHashedTokenForCurrentUser() throws Exception {
        String email = "change-" + System.nanoTime() + "@example.com";
        String access = registerAndLogin(email, "Str0ngPass!");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        clearInvocations(mailer);

        mockMvc.perform(post("/api/v1/auth/change-password-email")
                        .header("Authorization", auth(access)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        org.mockito.ArgumentCaptor<String> link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq(email), link.capture());
        URI uri = URI.create(link.getValue());
        assertThat(uri.getPath()).isEqualTo("/reset-password");
        String rawToken = URLDecoder.decode(uri.getRawQuery().substring("token=".length()), StandardCharsets.UTF_8);
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(stored.getUsedAt()).isNull();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(rawToken)))
                .andExpect(status().isNoContent());
        assertThat(passwordEncoder.matches("NewPass123", userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordEmailAnonymousIsUnauthorized() throws Exception {
        long before = passwordResetTokenRepository.count();
        clearInvocations(mailer);

        mockMvc.perform(post("/api/v1/auth/change-password-email"))
                .andExpect(status().isUnauthorized());

        assertThat(passwordResetTokenRepository.count()).isEqualTo(before);
        verify(mailer, never()).sendResetLink(anyString(), anyString());
    }

    @Test
    void changePasswordEmailIgnoresSuppliedAddress() throws Exception {
        String email = "owner-" + System.nanoTime() + "@example.com";
        String other = "other-" + System.nanoTime() + "@example.com";
        String access = registerAndLogin(email, "Str0ngPass!");
        registerAndLogin(other, "Str0ngPass!");
        clearInvocations(mailer);

        mockMvc.perform(post("/api/v1/auth/change-password-email")
                        .header("Authorization", auth(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(other)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(mailer).sendResetLink(eq(email), anyString());
        verify(mailer, never()).sendResetLink(eq(other), anyString());
    }

    @Test
    void changePasswordEmailWithoutEmailStoresNothing() throws Exception {
        String email = "blank-" + System.nanoTime() + "@example.com";
        String access = registerAndLogin(email, "Str0ngPass!");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmail(" ".repeat(8));
        userRepository.saveAndFlush(user);
        long before = passwordResetTokenRepository.count();
        clearInvocations(mailer);

        mockMvc.perform(post("/api/v1/auth/change-password-email")
                        .header("Authorization", auth(access)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(passwordResetTokenRepository.count()).isEqualTo(before);
        verify(mailer, never()).sendResetLink(anyString(), anyString());
        assertThat(passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    void changePasswordEmailStillNoContentWhenMailerFails() throws Exception {
        String email = "mailfail-" + System.nanoTime() + "@example.com";
        String access = registerAndLogin(email, "Str0ngPass!");
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(mailer).sendResetLink(eq(email), anyString());

        mockMvc.perform(post("/api/v1/auth/change-password-email")
                        .header("Authorization", auth(access)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).hasSize(1);
    }

    @Test
    void googleOnlyAccountCanSetPassword() throws Exception {
        String email = "google-only-" + System.nanoTime() + "@gmail.com";
        User user = new User();
        user.setEmail(email);
        user.setGoogleSub("gsub-" + System.nanoTime());
        user.setPasswordHash(null);
        user.setFirstName("Анна");
        user.setLastName("Смирнова");
        user.setRole(Role.FAN);
        user.setEnabled(true);
        userRepository.save(user);
        roleService.grantApproved(user, Role.FAN, null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        String rawToken = requestToken(email);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"NewPass123"}
                                """.formatted(rawToken)))
                .andExpect(status().isNoContent());

        var saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getGoogleSub()).startsWith("gsub-");
        assertThat(passwordEncoder.matches("NewPass123", saved.getPasswordHash())).isTrue();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPass123"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String requestToken(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        org.mockito.ArgumentCaptor<String> link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).sendResetLink(eq(email), link.capture());
        String url = link.getAllValues().get(link.getAllValues().size() - 1);
        URI uri = URI.create(url);
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPath()).isEqualTo("/reset-password");
        return URLDecoder.decode(uri.getRawQuery().substring("token=".length()), StandardCharsets.UTF_8);
    }

    private String loginRefresh(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();
    }
}
