package com.studentleague.players;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerCardIntegrationTest extends AbstractIntegrationTest {

    @Test
    void publicCardListsMatchesAndScoringActions() throws Exception {
        String adminToken = createAdminAndLogin("pcard-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupFinishedMatch(adminToken);

        mockMvc.perform(get("/api/v1/players/" + fx.scorerId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.appearances").value(1))
                .andExpect(jsonPath("$.statistics.goals").value(2))
                .andExpect(jsonPath("$.statistics.assists").value(0))
                .andExpect(jsonPath("$.statistics.yellowCards").value(1))
                .andExpect(jsonPath("$.statistics.redCards").value(0))
                .andExpect(jsonPath("$.matchHistory.length()").value(1))
                .andExpect(jsonPath("$.matchHistory[0].matchId").value(fx.matchId))
                .andExpect(jsonPath("$.matchHistory[0].opponentName").value("Away Side"))
                .andExpect(jsonPath("$.matchHistory[0].home").value(true))
                .andExpect(jsonPath("$.matchHistory[0].homeScore").value(2))
                .andExpect(jsonPath("$.matchHistory[0].awayScore").value(0))
                .andExpect(jsonPath("$.matchHistory[0].goals").value(2))
                .andExpect(jsonPath("$.matchHistory[0].assists").value(0))
                .andExpect(jsonPath("$.matchHistory[0].yellowCards").value(1))
                .andExpect(jsonPath("$.matchHistory[0].outcome").value("WIN"))
                .andExpect(jsonPath("$.matchHistory[0].tournamentName").value("Player Card Cup"));

        mockMvc.perform(get("/api/v1/players/" + fx.assisterId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.appearances").value(1))
                .andExpect(jsonPath("$.statistics.goals").value(0))
                .andExpect(jsonPath("$.statistics.assists").value(2))
                .andExpect(jsonPath("$.matchHistory[0].assists").value(2))
                .andExpect(jsonPath("$.matchHistory[0].goals").value(0))
                .andExpect(jsonPath("$.matchHistory[0].outcome").value("WIN"));

        mockMvc.perform(get("/api/v1/players/" + fx.keeperId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.appearances").value(1))
                .andExpect(jsonPath("$.statistics.goals").value(0))
                .andExpect(jsonPath("$.statistics.assists").value(0))
                .andExpect(jsonPath("$.statistics.cleanSheets").value(1))
                .andExpect(jsonPath("$.matchHistory.length()").value(1))
                .andExpect(jsonPath("$.matchHistory[0].goals").value(0))
                .andExpect(jsonPath("$.matchHistory[0].outcome").value("WIN"));
    }

    private Fixture setupFinishedMatch(String adminToken) throws Exception {
        String homeCapEmail = "pcard-hcap-" + System.nanoTime() + "@example.com";
        String awayCapEmail = "pcard-acap-" + System.nanoTime() + "@example.com";
        String mateEmail = "pcard-mate-" + System.nanoTime() + "@example.com";
        String gkEmail = "pcard-gk-" + System.nanoTime() + "@example.com";

        String homeToken = registerAndLogin(homeCapEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/h.jpg");
        String awayToken = registerAndLogin(awayCapEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/a.jpg");
        String mateToken = registerAndLogin(mateEmail, "Str0ngPass!", "PLAYER", "https://example.com/m.jpg");
        String gkToken = registerAndLogin(gkEmail, "Str0ngPass!", "PLAYER", "https://example.com/g.jpg");

        String scorerId = upsertPlayer(homeToken, "Home", "Scorer", 9, "FW");
        String awayPlayerId = upsertPlayer(awayToken, "Away", "Player", 10, "FW");
        String assisterId = upsertPlayer(mateToken, "Home", "Assist", 8, "MF");
        String keeperId = upsertPlayer(gkToken, "Home", "Keeper", 1, "Вратарь");

        String homeTeamId = createTeam(adminToken, "Home Side", scorerId);
        String awayTeamId = createTeam(adminToken, "Away Side", awayPlayerId);
        homeToken = reLogin(homeCapEmail, "Str0ngPass!");
        awayToken = reLogin(awayCapEmail, "Str0ngPass!");

        addMember(homeToken, homeTeamId, assisterId);
        addMember(homeToken, homeTeamId, keeperId);

        String sportId = footballSportId(adminToken);
        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Player Card Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        registerAndApprove(adminToken, homeToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().minus(2, ChronoUnit.HOURS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();

        String refereeEmail = "pcard-ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        referee.setRole(Role.REFEREE);
        userRepository.save(referee);
        String refereeToken = reLogin(refereeEmail, "Str0ngPass!");

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refereeId":"%s"}
                                """.formatted(referee.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/matches/" + matchId + "/lineups")
                        .header("Authorization", auth(refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s","%s","%s"]}
                                """.formatted(homeTeamId, scorerId, assisterId, keeperId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/matches/" + matchId + "/lineups")
                        .header("Authorization", auth(refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s"]}
                                """.formatted(awayTeamId, awayPlayerId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(refereeToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","secondaryPlayerId":"%s","gameTime":12}
                                """.formatted(homeTeamId, scorerId, assisterId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","secondaryPlayerId":"%s","gameTime":28}
                                """.formatted(homeTeamId, scorerId, assisterId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"YELLOW_CARD","teamId":"%s","playerId":"%s","gameTime":33}
                                """.formatted(homeTeamId, scorerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(2));

        return new Fixture(matchId, scorerId, assisterId, keeperId);
    }

    private String upsertPlayer(String token, String first, String last, int number, String position) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","jerseyNumber":%d,"position":"%s"}
                                """.formatted(first, last, number, position)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String captainToken, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated());
    }

    private void registerAndApprove(String adminToken, String captainToken, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, 2).toUpperCase(), captainPlayerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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

    private String reLogin(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private record Fixture(String matchId, String scorerId, String assisterId, String keeperId) {
    }
}
