package com.studentleague.tournaments;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentDateValidationTest extends AbstractIntegrationTest {

    @Test
    void rejectsFinishBeforeStartOnCreateAndUpdate() throws Exception {
        String adminToken = createAdminAndLogin("dates-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports")).andReturn().getResponse().getContentAsString()
        ).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Перепутанные даты","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"ACTIVE","startDate":"2026-11-20","endDate":"2026-11-10"}
                                """.formatted(sportId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Дата финиша не может быть раньше даты старта."));

        String tournamentId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/tournaments")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Ровные даты","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION","startDate":"2026-11-20","endDate":"2026-11-20"}
                                        """.formatted(sportId)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.startDate").value("2026-11-20"))
                        .andExpect(jsonPath("$.endDate").value("2026-11-20"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate":"2026-11-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Дата финиша не может быть раньше даты старта."));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2026-11-20"));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-11-01","endDate":"2026-11-30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.endDate").value("2026-11-30"));
    }
}
