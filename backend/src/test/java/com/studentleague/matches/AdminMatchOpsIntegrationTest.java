package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.repository.MatchAvailabilityRepository;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMatchOpsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchEventRepository matchEventRepository;
    @Autowired
    private MatchLineupPlayerRepository matchLineupPlayerRepository;
    @Autowired
    private MatchRefereeRepository matchRefereeRepository;
    @Autowired
    private MatchAvailabilityRepository matchAvailabilityRepository;

    @Test
    void adminDeletesMatchWithEventsLineupRefereeAndAvailability() throws Exception {
        String adminToken = createAdminAndLogin("del-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String strangerToken = registerAndLogin("del-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/matches/" + fx.matchId + "/availability")
                        .header("Authorization", auth(fx.homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"GOING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/matches/" + fx.matchId + "/lineups")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s"]}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isOk());

        String refereeEmail = "del-ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        referee.setRole(Role.REFEREE);
        userRepository.save(referee);
        mockMvc.perform(post("/api/v1/matches/" + fx.matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refereeId\":\"%s\"}".formatted(referee.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":4}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated());

        UUID matchId = UUID.fromString(fx.matchId);
        assertTrue(matchEventRepository.findByMatchIdOrderByTimestampAsc(matchId).size() >= 1);

        mockMvc.perform(delete("/api/v1/matches/" + fx.matchId)
                        .header("Authorization", auth(strangerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/matches/" + fx.matchId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isNotFound());
        assertTrue(matchEventRepository.findByMatchIdOrderByTimestampAsc(matchId).isEmpty());
        assertTrue(matchLineupPlayerRepository.findByMatchId(matchId).isEmpty());
        assertTrue(matchRefereeRepository.findByMatchId(matchId).isEmpty());
        assertTrue(matchAvailabilityRepository.findByMatchId(matchId).isEmpty());
    }

    @Test
    void adminGoalUpdatesScoreAndVoidRemovesIt() throws Exception {
        String adminToken = createAdminAndLogin("proto-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String strangerToken = registerAndLogin("proto-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":6}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isForbidden());

        MvcResult goal = mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":8}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("GOAL"))
                .andExpect(jsonPath("$.gameTime").value(480))
                .andExpect(jsonPath("$.voided").value(false))
                .andReturn();
        String eventId = objectMapper.readTree(goal.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"YELLOW_CARD","teamId":"%s","playerId":"%s","minute":9}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(strangerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(jsonPath("$.homeScore").value(1));

        mockMvc.perform(patch("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minute\":14}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameTime").value(840));

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(eventId)).value(true))
                .andExpect(jsonPath("$[?(@.eventType == 'YELLOW_CARD')].voided").value(false));
    }

    @Test
    void adminCorrectsFinishedMatchAndPublicScoreFollows() throws Exception {
        String adminToken = createAdminAndLogin("fin-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fanToken = registerAndLogin("fin-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String playerToken = registerAndLogin(
                "fin-player-" + System.nanoTime() + "@example.com",
                "Str0ngPass!",
                "PLAYER",
                "https://example.com/player.jpg"
        );
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));

        String goalBody = """
                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":11}
                """.formatted(fx.homeTeamId, fx.homePlayerId);
        for (String token : new String[] {fanToken, playerToken, fx.homeToken}) {
            mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                            .header("Authorization", auth(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(goalBody))
                    .andExpect(status().isForbidden());
        }

        MvcResult goal = mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("GOAL"))
                .andExpect(jsonPath("$.gameTime").value(660))
                .andExpect(jsonPath("$.voided").value(false))
                .andReturn();
        String eventId = objectMapper.readTree(goal.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"YELLOW_CARD","teamId":"%s","playerId":"%s","minute":12}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated());

        MvcResult pub = mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andReturn();
        String tournamentId = objectMapper.readTree(pub.getResponse().getContentAsString()).get("tournamentId").asText();

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].eventType".formatted(eventId)).value("GOAL"))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(eventId)).value(false))
                .andExpect(jsonPath("$[?(@.id == '%s')].playerId".formatted(eventId)).value(fx.homePlayerId));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(fx.homeTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(fx.homeTeamId)).value(3));

        mockMvc.perform(get("/api/v1/statistics/scorers").param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.playerId == '%s')].goals".formatted(fx.homePlayerId)).value(1));

        mockMvc.perform(patch("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minute\":14}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameTime").value(840));

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(eventId)).value(true));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(fx.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(fx.homeTeamId)).value(1));

        mockMvc.perform(get("/api/v1/statistics/scorers").param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.playerId == '%s')].goals".formatted(fx.homePlayerId)).value(0));
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String homeEmail = "ops-home-" + System.nanoTime() + "@example.com";
        String awayEmail = "ops-away-" + System.nanoTime() + "@example.com";
        String homeToken = registerAndLogin(homeEmail, "Str0ngPass!");
        String awayToken = registerAndLogin(awayEmail, "Str0ngPass!");

        MvcResult homePlayer = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Home","lastName":"Player","jerseyNumber":9}
                                """))
                .andExpect(status().isOk())
                .andReturn();
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

        String homeTeamId = createTeam(adminToken, "Ops Home " + System.nanoTime(), homePlayerId);
        String awayTeamId = createTeam(adminToken, "Ops Away " + System.nanoTime(), awayPlayerId);

        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
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
                                {"name":"Ops Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = reLogin(homeEmail, "Str0ngPass!");
        awayToken = reLogin(awayEmail, "Str0ngPass!");
        registerAndApprove(adminToken, homeToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();
        return new Fixture(matchId, homeTeamId, homePlayerId, homeToken);
    }

    private void registerAndApprove(String adminToken, String captainToken, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        String compact = name.replaceAll("\\s+", "");
        String shortName = compact.substring(Math.max(0, compact.length() - 12));
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, shortName, captainPlayerId)))
                .andExpect(status().isCreated())
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

    private record Fixture(String matchId, String homeTeamId, String homePlayerId, String homeToken) {
    }
}
