package com.studentleague.auth;

import com.studentleague.auth.google.GoogleIdTokenParser;
import com.studentleague.auth.google.GoogleIdentity;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleAuthIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private GoogleIdTokenParser parser;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @DynamicPropertySource
    static void reservedAdmin(DynamicPropertyRegistry registry) {
        registry.add("app.admin.email", () -> "reserved-admin@example.com");
    }

    @Test
    void newGoogleUserIsFanWithoutPassword() throws Exception {
        String email = "google-" + System.nanoTime() + "@gmail.com";
        when(parser.parse(eq("new-token"), any())).thenReturn(identity("sub-" + email, email, true, "Мария", "Соколова", "https://example.com/a.png"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("FAN"))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.roles[0].role").value("FAN"))
                .andExpect(jsonPath("$.user.photoUrl").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleSub()).isEqualTo("sub-" + email);
        assertThat(user.getRole()).isEqualTo(Role.FAN);
        assertThat(playerProfileRepository.findByUserId(user.getId())).isPresent();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        long users = userRepository.count();
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
        assertThat(userRepository.count()).isEqualTo(users);
    }

    @Test
    void httpPictureIsNotStored() throws Exception {
        String email = "pic-" + System.nanoTime() + "@gmail.com";
        when(parser.parse(eq("pic-token"), any())).thenReturn(identity("sub-" + email, email, true, "Иван", "Иванов", "http://example.com/a.png"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"pic-token\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getPhotoUrl()).isNull();
    }

    @Test
    void existingEmailIsLinkedAndPasswordStillWorks() throws Exception {
        String email = "link-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        when(parser.parse(eq("link-token"), any())).thenReturn(identity("linked-sub", email, true, "Иван", "Иванов", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"link-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("FAN"));

        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getGoogleSub()).isEqualTo("linked-sub");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void emailAlreadyLinkedToAnotherGoogleAccountConflicts() throws Exception {
        String email = "taken-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setGoogleSub("original-sub");
        userRepository.save(user);
        when(parser.parse(eq("other-token"), any())).thenReturn(identity("other-sub", email, true, "Иван", "Иванов", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"other-token\"}"))
                .andExpect(status().isConflict());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getGoogleSub()).isEqualTo("original-sub");
    }

    @Test
    void subjectLoginDoesNotTakeAnotherUsersEmail() throws Exception {
        String emailA = "a-" + System.nanoTime() + "@example.com";
        String emailB = "b-" + System.nanoTime() + "@example.com";
        registerAndLogin(emailA, "Str0ngPass!");
        registerAndLogin(emailB, "Str0ngPass!");
        var userA = userRepository.findByEmailIgnoreCase(emailA).orElseThrow();
        userA.setGoogleSub("sub-a");
        userRepository.save(userA);
        when(parser.parse(eq("sub-token"), any())).thenReturn(identity("sub-a", emailB, true, "Иван", "Иванов", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"sub-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(emailA));

        assertThat(userRepository.findByEmailIgnoreCase(emailA).orElseThrow().getGoogleSub()).isEqualTo("sub-a");
        assertThat(userRepository.findByEmailIgnoreCase(emailB).orElseThrow().getGoogleSub()).isNull();
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        String email = "unverified-" + System.nanoTime() + "@gmail.com";
        when(parser.parse(eq("unverified"), any())).thenReturn(identity("sub-u", email, false, "Анна", "Смирнова", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"unverified\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void invalidTokenIsUnauthorizedWithoutCreatingUser() throws Exception {
        when(parser.parse(eq("nope"), any())).thenReturn(null);
        long before = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    void reservedAdminEmailIsNotCreated() throws Exception {
        when(parser.parse(eq("admin-token"), any())).thenReturn(
                identity("sub-admin", "reserved-admin@example.com", true, "Админ", "Лиги", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"admin-token\"}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByEmailIgnoreCase("reserved-admin@example.com")).isEmpty();
    }

    @Test
    void parserReceivesConfiguredAudience() throws Exception {
        when(parser.parse(eq("aud-token"), any())).thenReturn(
                identity("sub-aud", "aud-" + System.nanoTime() + "@gmail.com", true, "Анна", "Смирнова", null));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"aud-token\"}"))
                .andExpect(status().isOk());

        verify(parser).parse(eq("aud-token"), eq(List.of("test-google-client")));
    }

    private static GoogleIdentity identity(String subject, String email, boolean verified, String given, String family, String picture) {
        return new GoogleIdentity(subject, email, verified, given, family, picture);
    }
}
