package com.studentleague.players;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerSearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void searchMatchesRegistrationNamesAndNotTheShirtNickname() throws Exception {
        String token = Long.toString(System.nanoTime(), 36);
        String last = "Иванов" + token;
        String first = "Пётр" + token;
        String shirt = "Vanya" + token;

        String playerToken = registerAndLogin(
                "shirt-" + token + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        MvcResult saved = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","displayName":"%s","jerseyNumber":9,"position":"Нападающий"}
                                """.formatted(first, last, shirt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(shirt))
                .andExpect(jsonPath("$.lastName").value(last))
                .andExpect(jsonPath("$.firstName").value(first))
                .andReturn();
        String playerId = objectMapper.readTree(saved.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/players").param("q", last))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId))
                .andExpect(jsonPath("$.content[0].lastName").value(last))
                .andExpect(jsonPath("$.content[0].firstName").value(first))
                .andExpect(jsonPath("$.content[0].displayName").value(shirt));

        mockMvc.perform(get("/api/v1/players").param("q", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("q", shirt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/players").param("q", "V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/players").param("q", last).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        String adminToken = createAdminAndLogin("search-admin-" + token + "@example.com", "Str0ngPass!");
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Search FC %s","shortName":"SF","captainPlayerId":"%s"}
                                """.formatted(token, playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = objectMapper.readTree(team.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/players").param("teamId", teamId).param("q", last))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("teamId", teamId).param("q", shirt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
