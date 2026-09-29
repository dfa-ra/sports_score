package com.studentleague.players;

import com.studentleague.auth.google.GoogleIdTokenParser;
import com.studentleague.auth.google.GoogleIdentity;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FanBecomePlayerIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private GoogleIdTokenParser parser;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Test
    void googleSignUpCreatesFanWithoutPlayerProfile() throws Exception {
        String email = "fan-new-" + System.nanoTime() + "@gmail.com";
        String token = googleFan(email, "https://lh3.googleusercontent.com/a/photo");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FAN"))
                .andExpect(jsonPath("$.roles[?(@.role=='PLAYER')]").isEmpty());

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.FAN);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(playerProfileRepository.findByUserId(user.getId())).isEmpty();
        mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fanRegistrationRequiresPhotoAndThenGrantsPlayer() throws Exception {
        String email = "fan-reg-" + System.nanoTime() + "@gmail.com";
        String token = googleFan(email, "https://lh3.googleusercontent.com/a/photo");

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Захар","lastName":"Захарченко","displayName":"Захар","jerseyNumber":7,"position":"Защитник"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Для регистрации игрока нужна фотография"));

        var stillFan = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stillFan.getRole()).isEqualTo(Role.FAN);
        assertThat(playerProfileRepository.findByUserId(stillFan.getId())).isEmpty();

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Захар","lastName":"Захарченко","displayName":"Захар","jerseyNumber":7,"position":"Защитник","bio":"люблю футзал","avatarUrl":"/media/avatars/z.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Захар"))
                .andExpect(jsonPath("$.lastName").value("Захарченко"))
                .andExpect(jsonPath("$.displayName").value("Захар"))
                .andExpect(jsonPath("$.jerseyNumber").value(7))
                .andExpect(jsonPath("$.position").value("Защитник"))
                .andExpect(jsonPath("$.avatarUrl").value("/media/avatars/z.png"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.roles[?(@.role=='PLAYER' && @.status=='APPROVED')]").exists());

        var player = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(player.getRole()).isEqualTo(Role.PLAYER);
        assertThat(player.getPasswordHash()).isNull();
        var profile = playerProfileRepository.findByUserId(player.getId()).orElseThrow();
        assertThat(profile.getPosition()).isEqualTo("Защитник");
        assertThat(profile.getAvatarUrl()).isEqualTo("/media/avatars/z.png");
    }

    @Test
    void anonymousPlayerRegistrationIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/players/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Захар","lastName":"Захарченко","displayName":"Захар","jerseyNumber":7,"position":"Защитник","avatarUrl":"/media/avatars/z.png"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String googleFan(String email, String picture) throws Exception {
        when(parser.parse(eq("token-" + email), any())).thenReturn(
                new GoogleIdentity("sub-" + email, email, true, "Захар", "Захарченко", picture));
        var result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("FAN"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
