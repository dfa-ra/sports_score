package com.studentleague.players;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisteredNameIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shirtNicknameIsNotTheRosterOrLineupTitle() throws Exception {
        String adminToken = createAdminAndLogin("names-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String playerToken = registerAndLogin(
                "names-ivan-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/ivan.jpg");
        String awayToken = registerAndLogin(
                "names-away-" + System.nanoTime() + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/away.jpg");

        String playerId = objectMapper.readTree(
                mockMvc.perform(put("/api/v1/players/me")
                                .header("Authorization", auth(playerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"firstName":"Иван","lastName":"Иванов","displayName":"Vanya","jerseyNumber":9,"position":"Нападающий"}
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.displayName").value("Vanya"))
                        .andExpect(jsonPath("$.firstName").value("Иван"))
                        .andExpect(jsonPath("$.lastName").value("Иванов"))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        String awayId = objectMapper.readTree(
                mockMvc.perform(put("/api/v1/players/me")
                                .header("Authorization", auth(awayToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"firstName":"Пётр","lastName":"Петров","displayName":"Petya","jerseyNumber":1,"position":"Вратарь"}
                                        """))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        String homeTeamId = createTeam(adminToken, "Имена Хоум " + System.nanoTime(), playerId);
        String awayTeamId = createTeam(adminToken, "Имена Гости " + System.nanoTime(), awayId);

        mockMvc.perform(get("/api/v1/teams/" + homeTeamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(playerId))
                .andExpect(jsonPath("$[0].playerLastName").value("Иванов"))
                .andExpect(jsonPath("$[0].playerFirstName").value("Иван"))
                .andExpect(jsonPath("$[0].displayName").value("Vanya"));

        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get(0).get("id").asText();
        String tournamentId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/tournaments")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Имена %s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                        """.formatted(System.nanoTime(), sportId)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
        registerAndApprove(adminToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        String matchId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/matches")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                        """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        mockMvc.perform(put("/api/v1/matches/" + matchId + "/lineups")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s"]}
                                """.formatted(homeTeamId, playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home.starters[0].lastName").value("Иванов"))
                .andExpect(jsonPath("$.home.starters[0].firstName").value("Иван"))
                .andExpect(jsonPath("$.home.starters[0].name").value("Vanya"));

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"GOING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Иванов"))
                .andExpect(jsonPath("$.firstName").value("Иван"))
                .andExpect(jsonPath("$.displayName").value("Vanya"));

        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":7}
                                """.formatted(homeTeamId, playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerLastName").value("Иванов"))
                .andExpect(jsonPath("$.playerFirstName").value("Иван"))
                .andExpect(jsonPath("$.playerName").value("Vanya"));

        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastGoalScorer").value("Иванов Иван"));

        mockMvc.perform(get("/api/v1/statistics/scorers").param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(playerId))
                .andExpect(jsonPath("$[0].lastName").value("Иванов"))
                .andExpect(jsonPath("$[0].firstName").value("Иван"))
                .andExpect(jsonPath("$[0].displayName").value("Vanya"));
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(post("/api/v1/teams")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s","shortName":"ИМ","captainPlayerId":"%s"}
                                        """.formatted(name, captainPlayerId)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
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
}
