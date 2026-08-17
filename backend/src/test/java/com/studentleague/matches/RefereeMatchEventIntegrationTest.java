package com.studentleague.matches;

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

class RefereeMatchEventIntegrationTest extends AbstractIntegrationTest {

    @Test
    void assignedRefereeControlsMatchAndEventsAffectScore() throws Exception {
        String adminToken = createAdminAndLogin("radmin-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        Fixture fx = setupMatch(adminToken);

        String unassignedRefEmail = "unref-" + System.nanoTime() + "@example.com";
        registerAndLogin(unassignedRefEmail, "Str0ngPass!");
        var unassigned = userRepository.findByEmailIgnoreCase(unassignedRefEmail).orElseThrow();
        unassigned.setRole(Role.REFEREE);
        userRepository.save(unassigned);
        String unassignedToken = reLogin(unassignedRefEmail, "Str0ngPass!");

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(unassignedToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));

        MvcResult goal = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":12}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("GOAL"))
                .andReturn();
        String eventId = objectMapper.readTree(goal.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(0));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s"}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/finish")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1));
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String homeCapEmail = "hcap-" + System.nanoTime() + "@example.com";
        String awayCapEmail = "acap-" + System.nanoTime() + "@example.com";
        String homeToken = registerAndLogin(homeCapEmail, "Str0ngPass!");
        String awayToken = registerAndLogin(awayCapEmail, "Str0ngPass!");

        MvcResult homePlayer = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Home","lastName":"Player","jerseyNumber":9}
                                """))
                .andExpect(status().isOk()).andReturn();
        String homePlayerId = objectMapper.readTree(homePlayer.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(awayToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Away","lastName":"Player","jerseyNumber":10}
                                """))
                .andExpect(status().isOk());

        String homeTeamId = createTeam(homeToken, "Home Side");
        String awayTeamId = createTeam(awayToken, "Away Side");

        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk()).andReturn();
        String sportId = null;
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                sportId = node.get("id").asText();
                break;
            }
        }

        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ref Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated()).andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = reLogin(homeCapEmail, "Str0ngPass!");
        awayToken = reLogin(awayCapEmail, "Str0ngPass!");
        registerAndApprove(adminToken, homeToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated()).andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();

        String refereeEmail = "aref-" + System.nanoTime() + "@example.com";
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

        return new Fixture(matchId, homeTeamId, homePlayerId, refereeToken);
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

    private String createTeam(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s"}
                                """.formatted(name, name.substring(0, 2).toUpperCase())))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String reLogin(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private record Fixture(String matchId, String homeTeamId, String homePlayerId, String refereeToken) {
    }
}
