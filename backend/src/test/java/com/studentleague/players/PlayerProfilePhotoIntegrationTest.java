package com.studentleague.players;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerProfilePhotoIntegrationTest extends AbstractIntegrationTest {

    @Test
    void savingJerseyKeepsRegistrationPhoto() throws Exception {
        String token = registerAndLogin(
                "photo-keep-" + System.nanoTime() + "@example.com",
                "Str0ngPass!",
                "PLAYER",
                "https://cdn.example/player.jpg"
        );

        mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example/player.jpg"));

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Иван","lastName":"Петров","jerseyNumber":9}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jerseyNumber").value(9))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example/player.jpg"));

        mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example/player.jpg"));
    }
}
