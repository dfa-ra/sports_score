package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchMinuteElapsedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void countdownClockStoresElapsedFromKickoffAndProtocolFollowsThatMinute() throws Exception {
        String adminToken = createAdminAndLogin("min-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodLengthSeconds").value(15 * 60));

        // Hall clock shows 13:00 left in a 15:00 first half → 2:00 played.
        parkClock(fx.matchId, 1, 15 * 60 - 13 * 60);
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goal(fx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameTime").value(2 * 60))
                .andExpect(jsonPath("$.period").value(1));

        // 12:00 left in the second half → 3:00 played there → 18:00 from kickoff.
        parkClock(fx.matchId, 2, 15 * 60 - 12 * 60);
        MvcResult second = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goal(fx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameTime").value(18 * 60))
                .andExpect(jsonPath("$.period").value(2))
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        // Written after the 18' goal, but it happened at 11' of the first half.
        parkClock(fx.matchId, 1, 11 * 60);
        MvcResult insertedLate = mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goal(fx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameTime").value(11 * 60))
                .andReturn();
        String lateId = objectMapper.readTree(insertedLate.getResponse().getContentAsString()).get("id").asText();

        JsonNode events = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        );
        List<JsonNode> goals = new ArrayList<>();
        events.forEach(event -> {
            if ("GOAL".equals(event.get("eventType").asText()) && !event.get("voided").asBoolean()) {
                goals.add(event);
            }
        });
        assertEquals(3, goals.size());
        goals.sort(Comparator.comparingInt((JsonNode event) -> event.get("gameTime").asInt())
                .thenComparing(event -> event.get("timestamp").asText()));

        assertEquals(2 * 60, goals.get(0).get("gameTime").asInt());
        assertEquals(2, MatchClock.displayMinute(goals.get(0).get("gameTime").asInt()));
        assertNotEquals(13 * 60, goals.get(0).get("gameTime").asInt());

        assertEquals(lateId, goals.get(1).get("id").asText());
        assertEquals(11, MatchClock.displayMinute(goals.get(1).get("gameTime").asInt()));

        assertEquals(secondId, goals.get(2).get("id").asText());
        assertEquals(18 * 60, goals.get(2).get("gameTime").asInt());
        assertEquals(18, MatchClock.displayMinute(goals.get(2).get("gameTime").asInt()));
        assertNotEquals(12 * 60, goals.get(2).get("gameTime").asInt());
        assertNotEquals(3 * 60, goals.get(2).get("gameTime").asInt());
    }

    @Test
    void adminMinuteOnAFinishedMatchIsWhatALoggedOutViewerSees() throws Exception {
        String adminToken = createAdminAndLogin("min-fin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));

        MvcResult created = mockMvc.perform(post("/api/v1/admin/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":18}
                                """.formatted(fx.homeTeamId, fx.homePlayerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameTime").value(18 * 60))
                .andExpect(jsonPath("$.period").value(2))
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        JsonNode events = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        );
        JsonNode goal = null;
        for (JsonNode event : events) {
            if (eventId.equals(event.get("id").asText())) {
                goal = event;
            }
        }
        assertEquals(18 * 60, goal.get("gameTime").asInt());
        assertEquals(18, MatchClock.displayMinute(goal.get("gameTime").asInt()));
        assertEquals(2, goal.get("period").asInt());
    }

    private void parkClock(String matchId, int period, int elapsedInPeriod) {
        Match match = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        match.setStatus(MatchStatus.PAUSED);
        match.setClockRunningSince(null);
        match.setPeriod(period);
        match.setGameTimeSeconds(elapsedInPeriod);
        matchRepository.saveAndFlush(match);
    }

    private static String goal(Fixture fx) {
        return """
                {"eventType":"GOAL","teamId":"%s","playerId":"%s"}
                """.formatted(fx.homeTeamId, fx.homePlayerId);
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String homeEmail = "min-home-" + System.nanoTime() + "@example.com";
        String awayEmail = "min-away-" + System.nanoTime() + "@example.com";
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

        String homeTeamId = createTeam(adminToken, "Min Home " + System.nanoTime(), homePlayerId);
        String awayTeamId = createTeam(adminToken, "Min Away " + System.nanoTime(), awayPlayerId);

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
                                {"name":"Minute Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = reLogin(homeEmail, "Str0ngPass!");
        awayToken = reLogin(awayEmail, "Str0ngPass!");
        registerAndApprove(adminToken, homeToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();
        return new Fixture(matchId, homeTeamId, homePlayerId);
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

    private record Fixture(String matchId, String homeTeamId, String homePlayerId) {
    }
}
