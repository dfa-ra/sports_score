package com.studentleague.tournaments;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentStatusValidationTest extends AbstractIntegrationTest {

    @Test
    void unknownStatusIsBadRequest() throws Exception {
        String adminToken = createAdminAndLogin("tstatus-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports")).andReturn().getResponse().getContentAsString()
        ).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Чужой статус","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"ONGOING"}
                                """.formatted(sportId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Некорректное значение"));

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Чужой статус","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"IN_PROGRESS"}
                                """.formatted(sportId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное значение"));

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Живой турнир","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"ACTIVE"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
