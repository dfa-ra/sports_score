package com.studentleague.matches;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchKickoffRangeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fromAndToBoundScheduledAt() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("range-admin-" + stamp + "@example.com", "Str0ngPass!");
        String captainEmail = "range-cap-" + stamp + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Нина","lastName":"Дата"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "Range Home " + stamp, playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "range-other-" + stamp + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Глеб","lastName":"Дата"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Range Away " + stamp, playerId(otherToken));

        String tournamentId = createTournament(adminToken, sportId(adminToken));
        registerAndApprove(adminToken, captainToken, tournamentId, teamA);
        registerAndApprove(adminToken, otherToken, tournamentId, teamB);

        String early = createMatch(adminToken, tournamentId, teamA, teamB, "2099-06-01T09:00:00Z");
        String mid = createMatch(adminToken, tournamentId, teamA, teamB, "2099-06-15T12:00:00Z");
        String late = createMatch(adminToken, tournamentId, teamA, teamB, "2099-08-01T12:00:00Z");
        String finishedInRange = createMatch(adminToken, tournamentId, teamA, teamB, "2099-06-20T15:00:00Z");
        mockMvc.perform(post("/api/v1/referee/matches/" + finishedInRange + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + finishedInRange + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/matches")
                        .param("from", "2099-06-15T12:00:00Z")
                        .param("to", "2099-06-30T23:59:59Z")
                        .param("tournamentId", tournamentId)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(mid)).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(finishedInRange)).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(early)).isEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(late)).isEmpty());

        mockMvc.perform(get("/api/v1/matches")
                        .param("from", "2099-06-15")
                        .param("to", "2099-06-15")
                        .param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mid));

        mockMvc.perform(get("/api/v1/matches")
                        .param("from", "2099-06-01T00:00:00Z")
                        .param("to", "2099-08-02T00:00:00Z")
                        .param("status", "SCHEDULED")
                        .param("tournamentId", tournamentId)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(finishedInRange)).isEmpty());

        mockMvc.perform(get("/api/v1/matches").param("from", "not-a-date"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/matches")
                        .param("tournamentId", tournamentId)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void teamIdKeepsHomeOrAwayAndOmittingItListsTheTournament() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("team-admin-" + stamp + "@example.com", "Str0ngPass!");
        String captainEmail = "team-cap-" + stamp + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Nina","lastName":"Home"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "Filter Home " + stamp, playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "team-other-" + stamp + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Gleb","lastName":"Away"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Filter Away " + stamp, playerId(otherToken));

        String thirdEmail = "team-third-" + stamp + "@example.com";
        String thirdToken = registerAndLogin(thirdEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(thirdToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Oleg","lastName":"Third"}
                                """))
                .andExpect(status().isOk());
        String teamC = createTeam(adminToken, "Filter Third " + stamp, playerId(thirdToken));

        String tournamentId = createTournament(adminToken, sportId(adminToken));
        registerAndApprove(adminToken, captainToken, tournamentId, teamA);
        registerAndApprove(adminToken, otherToken, tournamentId, teamB);
        registerAndApprove(adminToken, thirdToken, tournamentId, teamC);

        String home = createMatch(adminToken, tournamentId, teamA, teamB, "2099-07-01T09:00:00Z");
        String away = createMatch(adminToken, tournamentId, teamB, teamA, "2099-07-02T09:00:00Z");
        String other = createMatch(adminToken, tournamentId, teamC, teamB, "2099-07-03T09:00:00Z");

        mockMvc.perform(get("/api/v1/matches")
                        .param("teamId", teamA)
                        .param("tournamentId", tournamentId)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(home)).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(away)).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(other)).isEmpty());

        mockMvc.perform(get("/api/v1/matches")
                        .param("teamId", teamA)
                        .param("status", "FINISHED")
                        .param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/matches")
                        .param("tournamentId", tournamentId)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(other)).isNotEmpty());
    }

    private String createMatch(String adminToken, String tournamentId, String homeId, String awayId, String kickoff) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeId, awayId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
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

    private String createTournament(String adminToken, String sportId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Range Cup","sportId":"%s","seasonYear":2099,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String sportId(String adminToken) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"RG","captainPlayerId":"%s"}
                                """.formatted(name, captainPlayerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String playerId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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
}
