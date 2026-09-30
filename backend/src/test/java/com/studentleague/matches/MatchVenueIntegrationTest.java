package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchVenueIntegrationTest extends AbstractIntegrationTest {

    @Test
    void creatingMatchWithVenueReturnsItAndOmittingVenueStillWorks() throws Exception {
        String adminToken = createAdminAndLogin("venue-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "venue-cap-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Vera","lastName":"Hall"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "North Hall", playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "venue-other-" + System.nanoTime() + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Igor","lastName":"Pitch"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "South Hall", playerId(otherToken));
        otherToken = reLogin(otherEmail, "Str0ngPass!");

        String tournamentId = createTournament(adminToken, sportId(adminToken));
        registerAndApprove(adminToken, captainToken, tournamentId, teamA);
        registerAndApprove(adminToken, otherToken, tournamentId, teamB);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult created = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"  Кронверкский манеж  "}
                                """.formatted(tournamentId, teamA, teamB, kickoff)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venue").value("Кронверкский манеж"))
                .andReturn();
        String matchId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("Кронверкский манеж"));

        mockMvc.perform(get("/api/v1/matches").param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(matchId))
                .andExpect(jsonPath("$.content[0].venue").value("Кронверкский манеж"));

        mockMvc.perform(put("/api/v1/matches/" + matchId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"venue":"Зал ИТМО"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("Зал ИТМО"));

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, teamA, teamB, kickoff)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.venue").value(nullValue()));
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
                                {"name":"Venue Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
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
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, 2).toUpperCase(), captainPlayerId)))
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
