package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentMatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void tournamentRegistrationAndMatchScheduling() throws Exception {
        String adminToken = createAdminAndLogin("tadmin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "tcaptain-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        String otherToken = registerAndLogin("tother-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Bob","lastName":"Other"}
                                """))
                .andExpect(status().isOk());

        String captainPlayerId = playerId(captainToken);
        String teamA = createTeam(adminToken, "Alpha FC", captainPlayerId);
        captainToken = reLogin(captainEmail, "Str0ngPass!");
        String teamBCaptainEmail = "tb-" + System.nanoTime() + "@example.com";
        String teamBToken = registerAndLogin(teamBCaptainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(teamBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Bee","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Beta FC", playerId(teamBToken));

        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String sportId = objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spring Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isForbidden());

        MvcResult tournamentResult = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spring Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spring Cup"))
                .andReturn();
        String tournamentId = objectMapper.readTree(tournamentResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        teamBToken = reLogin(teamBCaptainEmail, "Str0ngPass!");
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(teamBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamB)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamA + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamB + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());

        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        MvcResult matchResult = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, teamA, teamB, kickoff)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        String matchId = objectMapper.readTree(matchResult.getResponse().getContentAsString()).get("id").asText();

        String refereeEmail = "ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var refUser = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        refUser.setRole(Role.REFEREE);
        userRepository.save(refUser);

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refereeId":"%s"}
                                """.formatted(refUser.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.tables[0].rows.length()").value(2));

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/teams/" + teamB)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + teamB + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + teamA + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows.length()").value(1));

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamB)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamB + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        MvcResult played = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, teamA, teamB, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        String playedId = objectMapper.readTree(played.getResponse().getContentAsString()).get("id").asText();
        var finished = matchRepository.findById(UUID.fromString(playedId)).orElseThrow();
        finished.setStatus(MatchStatus.FINISHED);
        finished.setHomeScore(2);
        finished.setAwayScore(1);
        matchRepository.saveAndFlush(finished);

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/teams/" + teamB)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/matches/" + playedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows.length()").value(1))
                .andExpect(jsonPath("$.tables[0].rows[0].teamId").value(teamA))
                .andExpect(jsonPath("$.tables[0].rows[0].played").value(0));

        mockMvc.perform(delete("/api/v1/teams/" + teamB)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/teams/" + teamB)
                        .header("Authorization", auth(adminToken))
                        .param("purge", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/teams/" + teamB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbanded").value(true));
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
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
