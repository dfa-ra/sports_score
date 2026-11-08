package com.studentleague.players;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        String full = last + " " + first;
        mockMvc.perform(get("/api/v1/players").param("q", "  " + full.toUpperCase(Locale.ROOT) + "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("q", first + "   " + last))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("q", "Хрюков" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

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

        mockMvc.perform(get("/api/v1/players").param("teamId", teamId).param("q", "  " + last + "   " + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(playerId));

        mockMvc.perform(get("/api/v1/players").param("teamId", teamId).param("q", shirt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchFullNamesStayInsideTheSelectedTournament() throws Exception {
        String token = Long.toString(System.nanoTime(), 36);
        String orlovFirst = "Илья";
        String orlovLast = "Орлов" + token;
        String tolyanFirst = "Толян";
        String tolyanLast = "Игроков" + token;

        String orlovToken = registerAndLogin("orlov-" + token + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/o.jpg");
        String tolyanToken = registerAndLogin("tolyan-" + token + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/t.jpg");
        String orlovId = upsert(orlovToken, orlovFirst, orlovLast);
        String tolyanId = upsert(tolyanToken, tolyanFirst, tolyanLast);

        String adminToken = createAdminAndLogin("search-tour-" + token + "@example.com", "Str0ngPass!");
        String orlovTeam = createTeam(adminToken, "Орловы " + token, orlovId);
        String tolyanTeam = createTeam(adminToken, "Игроковы " + token, tolyanId);
        String sportId = footballSportId(adminToken);
        String liga = createTournament(adminToken, "Лига " + token, sportId);
        String cup = createTournament(adminToken, "Кубок " + token, sportId);
        registerAndApprove(adminToken, liga, orlovTeam);
        registerAndApprove(adminToken, cup, tolyanTeam);

        assertEquals(Set.of(orlovId), ids(mockMvc.perform(get("/api/v1/players")
                .param("q", "  " + orlovLast.toUpperCase(Locale.ROOT) + "   " + orlovFirst.toLowerCase(Locale.ROOT) + "  ")
                .param("tournamentId", liga))
                .andExpect(status().isOk())
                .andReturn()));
        assertEquals(Set.of(), ids(mockMvc.perform(get("/api/v1/players")
                .param("q", tolyanLast + " " + tolyanFirst)
                .param("tournamentId", liga))
                .andExpect(status().isOk())
                .andReturn()));
        assertEquals(Set.of(orlovId), ids(mockMvc.perform(get("/api/v1/players")
                .param("q", token)
                .param("tournamentId", liga))
                .andExpect(status().isOk())
                .andReturn()));

        assertEquals(Set.of(tolyanId), ids(mockMvc.perform(get("/api/v1/players")
                .param("q", "  игроков" + token + "    толян ")
                .param("tournamentId", cup))
                .andExpect(status().isOk())
                .andReturn()));
        assertEquals(Set.of(), ids(mockMvc.perform(get("/api/v1/players")
                .param("q", orlovLast + " " + orlovFirst)
                .param("tournamentId", cup))
                .andExpect(status().isOk())
                .andReturn()));

        Set<String> both = ids(mockMvc.perform(get("/api/v1/players").param("q", token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(both.contains(orlovId));
        assertTrue(both.contains(tolyanId));
        assertFalse(ids(mockMvc.perform(get("/api/v1/players").param("q", "Хрюков" + token))
                .andExpect(status().isOk())
                .andReturn()).contains(orlovId));
    }

    private String upsert(String token, String first, String last) throws Exception {
        MvcResult saved = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","displayName":"Shirt%s","jerseyNumber":9,"position":"Нападающий"}
                                """.formatted(first, last, last)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(saved.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTeam(String adminToken, String name, String captainId) throws Exception {
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"SF","captainPlayerId":"%s"}
                                """.formatted(name, captainId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(team.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTournament(String adminToken, String name, String sportId) throws Exception {
        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(name, sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
    }

    private void registerAndApprove(String adminToken, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private String footballSportId(String adminToken) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        return objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private Set<String> ids(MvcResult result) throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString()).get("content")) {
            ids.add(node.get("id").asText());
        }
        return ids;
    }
}
