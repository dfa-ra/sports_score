package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefereePadAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void unassignedRefereeIsForbiddenAndAssignedRefereeOrAdminCanUseThePad() throws Exception {
        String adminToken = createAdminAndLogin("pad-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(fx.unassignedToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.unassignedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/referee/matches/" + fx.matchId + "/analysts")
                        .header("Authorization", auth(fx.unassignedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"BOTH","userId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/referee/matches/" + fx.matchId + "/goalkeeper")
                        .header("Authorization", auth(fx.unassignedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","playerId":"%s"}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("GOAL"));
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"YELLOW_CARD","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","metadata":{"ownGoal":true}}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1));

        mockMvc.perform(put("/api/v1/referee/matches/" + fx.matchId + "/analysts")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"BOTH","userId":"%s"}
                                """.formatted(fx.analystId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("BOTH"));
        mockMvc.perform(put("/api/v1/referee/matches/" + fx.matchId + "/analysts")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"BOTH","userId":"%s"}
                                """.formatted(fx.analystId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/next-period")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(2));

        JsonNode events = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        Integer periodEnd = gameTimeOf(events, "PERIOD_END");
        Integer periodStart = gameTimeOf(events, "PERIOD_START", 2);
        assertTrue(periodEnd != null && periodEnd < 15 * 60, "period end is elapsed time, not the 15:00 mark");
        assertEquals(periodEnd, periodStart, "the next period starts at the same kickoff second, not at 15:00");
    }

    @Test
    void assignedRefereeVoidsAnEventUntilTheMatchIsFinished() throws Exception {
        String adminToken = createAdminAndLogin("void-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + UUID.randomUUID() + "/void")
                        .header("Authorization", auth(fx.unassignedToken)))
                .andExpect(status().isForbidden());

        MvcResult started = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andReturn();
        String tournamentId = objectMapper.readTree(started.getResponse().getContentAsString()).get("tournamentId").asText();
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult goal = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s"}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String eventId = objectMapper.readTree(goal.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(jsonPath("$.homeScore").value(1));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(fx.unassignedToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));
        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(jsonPath("$.homeScore").value(0));

        MvcResult second = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s"}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/finish")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + secondId + "/void")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events/" + secondId + "/void")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));
        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(jsonPath("$.homeScore").value(0));
    }

    private static Integer gameTimeOf(JsonNode events, String type) {
        return gameTimeOf(events, type, null);
    }

    private static Integer gameTimeOf(JsonNode events, String type, Integer period) {
        for (JsonNode event : events) {
            if (!type.equals(event.path("eventType").asText())) {
                continue;
            }
            if (period != null && event.path("period").asInt() != period) {
                continue;
            }
            return event.get("gameTime").asInt();
        }
        return null;
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String homeToken = registerAndLogin("pad-home-" + stamp + "@example.com", "Str0ngPass!");
        String awayToken = registerAndLogin("pad-away-" + stamp + "@example.com", "Str0ngPass!");

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
        String awayPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(awayToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        String homeTeamId = createTeam(adminToken, "Pad Home " + stamp, homePlayerId);
        String awayTeamId = createTeam(adminToken, "Pad Away " + stamp, awayPlayerId);
        String sportId = footballId(adminToken);

        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pad Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated()).andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
        approve(adminToken, homeToken, tournamentId, homeTeamId);
        approve(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated()).andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();

        String refereeToken = refereeToken("pad-ref-" + stamp + "@example.com");
        String unassignedToken = refereeToken("pad-other-" + stamp + "@example.com");
        var referee = userRepository.findByEmailIgnoreCase("pad-ref-" + stamp + "@example.com").orElseThrow();
        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refereeId":"%s"}
                                """.formatted(referee.getId())))
                .andExpect(status().isCreated());

        String analystEmail = "pad-an-" + stamp + "@example.com";
        registerAndLogin(analystEmail, "Str0ngPass!");
        var analyst = userRepository.findByEmailIgnoreCase(analystEmail).orElseThrow();
        roleService.grantApproved(analyst, Role.ANALYST, null);

        return new Fixture(matchId, homeTeamId, homePlayerId, refereeToken, unassignedToken, analyst.getId().toString());
    }

    private String refereeToken(String email) throws Exception {
        registerAndLogin(email, "Str0ngPass!");
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(Role.REFEREE);
        userRepository.save(user);
        roleService.grantApproved(user, Role.REFEREE, null);
        return loginOnly(email, "Str0ngPass!");
    }

    private String footballId(String adminToken) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("FOOTBALL sport is not seeded");
    }

    private void approve(String adminToken, String captainToken, String tournamentId, String teamId) throws Exception {
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
        String shortName = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (shortName.length() < 2) {
            shortName = "TM";
        }
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, shortName.substring(0, Math.min(4, shortName.length())), captainPlayerId)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private record Fixture(
            String matchId,
            String homeTeamId,
            String homePlayerId,
            String refereeToken,
            String unassignedToken,
            String analystId
    ) {
    }
}
