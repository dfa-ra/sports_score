package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OwnGoalKeeperIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ownGoalScoresForTheOpponentAndSkipsTheScorerUntilAnAdminClearsIt() throws Exception {
        String adminToken = createAdminAndLogin("og-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fanToken = registerAndLogin("og-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Referee referee = referee();

        String scorerToken = registerAndLogin("og-scorer-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String assistToken = registerAndLogin("og-assist-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String awayToken = registerAndLogin("og-away-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String scorerId = upsert(scorerToken, "Home", "Scorer", 9, "Нападающий");
        String assistId = upsert(assistToken, "Home", "Assist", 8, "Полузащитник");
        String awayId = upsert(awayToken, "Away", "Player", 10, "Нападающий");

        String homeTeamId = createTeam(adminToken, "OG Home " + System.nanoTime(), scorerId);
        String awayTeamId = createTeam(adminToken, "OG Away " + System.nanoTime(), awayId);
        addMember(adminToken, homeTeamId, assistId);

        String sportId = footballSportId(adminToken);
        String tournamentId = tournament(adminToken, sportId, "Own Goal Cup");
        registerAndApprove(adminToken, adminToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, adminToken, tournamentId, awayTeamId);
        String matchId = match(adminToken, tournamentId, homeTeamId, awayTeamId);

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refereeId\":\"%s\"}".formatted(referee.id)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(referee.token)))
                .andExpect(status().isOk());

        String ownGoalBody = """
                {"eventType":"GOAL","teamId":"%s","playerId":"%s","secondaryPlayerId":"%s","metadata":{"ownGoal":true}}
                """.formatted(homeTeamId, scorerId, assistId);
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownGoalBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":4,"ownGoal":true}
                                """.formatted(homeTeamId, scorerId)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(referee.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownGoalBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("GOAL"))
                .andExpect(jsonPath("$.metadata.ownGoal").value(true))
                .andExpect(jsonPath("$.secondaryPlayerId").value(nullValue()))
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(1));
        JsonNode published = eventById(matchId, eventId);
        assertEquals("GOAL", published.get("eventType").asText());
        assertTrue(published.path("metadata").path("ownGoal").asBoolean(false));
        assertTrue(published.get("secondaryPlayerId") == null || published.get("secondaryPlayerId").isNull());
        assertEquals(1, goalCount(matchId));

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(referee.token)))
                .andExpect(status().isOk());

        assertGoalsFor(tournamentId, homeTeamId, 0);
        assertGoalsFor(tournamentId, awayTeamId, 1);
        assertEquals(0, scorerGoals(tournamentId, scorerId));
        assertEquals(0, assistCount(tournamentId, assistId));
        assertEquals(0, teamGoals(homeTeamId, scorerId));

        mockMvc.perform(patch("/api/v1/admin/matches/" + matchId + "/events/" + eventId)
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minute":8,"playerId":"%s","secondaryPlayerId":"%s","updatePlayers":true,"ownGoal":false}
                                """.formatted(scorerId, assistId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/matches/" + matchId + "/events/" + eventId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minute":8,"playerId":"%s","secondaryPlayerId":"%s","updatePlayers":true,"ownGoal":false}
                                """.formatted(scorerId, assistId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameTime").value(480))
                .andExpect(jsonPath("$.secondaryPlayerId").value(assistId));
        JsonNode cleared = eventById(matchId, eventId);
        assertFalse(cleared.path("metadata").path("ownGoal").asBoolean(false));
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));
        assertGoalsFor(tournamentId, homeTeamId, 1);
        assertGoalsFor(tournamentId, awayTeamId, 0);
        assertEquals(1, scorerGoals(tournamentId, scorerId));
        assertEquals(1, assistCount(tournamentId, assistId));
        assertEquals(1, teamGoals(homeTeamId, scorerId));

        mockMvc.perform(patch("/api/v1/admin/matches/" + matchId + "/events/" + eventId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minute":8,"playerId":"%s","secondaryPlayerId":"%s","updatePlayers":true,"ownGoal":true}
                                """.formatted(scorerId, assistId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.ownGoal").value(true))
                .andExpect(jsonPath("$.secondaryPlayerId").value(nullValue()));
        assertTrue(eventById(matchId, eventId).path("metadata").path("ownGoal").asBoolean(false));
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(1));
        assertEquals(0, scorerGoals(tournamentId, scorerId));
        assertEquals(0, assistCount(tournamentId, assistId));
        assertEquals(0, teamGoals(homeTeamId, scorerId));
        assertGoalsFor(tournamentId, awayTeamId, 1);

        MvcResult added = mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":12,"ownGoal":true}
                                """.formatted(awayTeamId, awayId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.ownGoal").value(true))
                .andExpect(jsonPath("$.gameTime").value(720))
                .andReturn();
        String addedId = objectMapper.readTree(added.getResponse().getContentAsString()).get("id").asText();
        assertTrue(eventById(matchId, addedId).path("metadata").path("ownGoal").asBoolean(false));
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1));
        assertEquals(0, scorerGoals(tournamentId, awayId));
        assertEquals(0, teamGoals(awayTeamId, awayId));
        assertGoalsFor(tournamentId, homeTeamId, 1);
        assertGoalsFor(tournamentId, awayTeamId, 1);
    }

    @Test
    void refereeOrAdminChoosesTheGoalkeeperAndAFanCannot() throws Exception {
        String adminToken = createAdminAndLogin("gk-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fanToken = registerAndLogin("gk-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Referee referee = referee();

        String keeperToken = registerAndLogin("gk-keep-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String fieldToken = registerAndLogin("gk-field-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String spareToken = registerAndLogin("gk-spare-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String awayToken = registerAndLogin("gk-away-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String keeperId = upsert(keeperToken, "Home", "Keeper", 1, "Вратарь");
        String fieldId = upsert(fieldToken, "Home", "Forward", 9, "Нападающий");
        String spareId = upsert(spareToken, "Home", "Spare", 12, "Защитник");
        String awayId = upsert(awayToken, "Away", "Player", 10, "Нападающий");

        String homeTeamId = createTeam(adminToken, "GK Home " + System.nanoTime(), fieldId);
        String awayTeamId = createTeam(adminToken, "GK Away " + System.nanoTime(), awayId);
        addMember(adminToken, homeTeamId, keeperId);
        addMember(adminToken, homeTeamId, spareId);

        String sportId = footballSportId(adminToken);
        String tournamentId = tournament(adminToken, sportId, "Keeper Cup");
        registerAndApprove(adminToken, adminToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, adminToken, tournamentId, awayTeamId);
        String matchId = match(adminToken, tournamentId, homeTeamId, awayTeamId);

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refereeId\":\"%s\"}".formatted(referee.id)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/matches/" + matchId + "/lineups")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s","%s"],"benchPlayerIds":[]}
                                """.formatted(homeTeamId, keeperId, fieldId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(referee.token)))
                .andExpect(status().isOk());

        String fieldBody = "{\"teamId\":\"%s\",\"playerId\":\"%s\"}".formatted(homeTeamId, fieldId);
        mockMvc.perform(put("/api/v1/referee/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/referee/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(referee.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"playerId\":\"%s\"}".formatted(homeTeamId, spareId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/referee/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(referee.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoalkeeper.playerId").value(fieldId))
                .andExpect(jsonPath("$.homeGoalkeeper.firstName").value("Home"))
                .andExpect(jsonPath("$.homeGoalkeeper.lastName").value("Forward"));

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(referee.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s"}
                                """.formatted(homeTeamId, fieldId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(referee.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));

        assertEquals(1, cleanSheets(tournamentId, fieldId));
        assertEquals(0, cleanSheets(tournamentId, keeperId));

        mockMvc.perform(put("/api/v1/admin/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"playerId\":null}".formatted(homeTeamId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoalkeeper").value(nullValue()));
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoalkeeper").value(nullValue()));
        assertEquals(0, cleanSheets(tournamentId, fieldId));
        assertEquals(1, cleanSheets(tournamentId, keeperId));

        mockMvc.perform(put("/api/v1/admin/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoalkeeper.playerId").value(fieldId));
        assertEquals(1, cleanSheets(tournamentId, fieldId));
        assertEquals(0, cleanSheets(tournamentId, keeperId));
    }

    private record Referee(String token, String id) {
    }

    private Referee referee() throws Exception {
        String email = "og-ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        referee.setRole(Role.REFEREE);
        userRepository.save(referee);
        return new Referee(reLogin(email, "Str0ngPass!"), referee.getId().toString());
    }

    private int cleanSheets(String tournamentId, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/statistics/goalkeepers")
                        .param("tournamentId", tournamentId)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (playerId.equals(node.path("playerId").asText())) {
                return node.path("cleanSheets").asInt();
            }
        }
        return 0;
    }

    private int scorerGoals(String tournamentId, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/statistics/scorers")
                        .param("tournamentId", tournamentId)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (playerId.equals(node.path("playerId").asText())) {
                return node.path("goals").asInt();
            }
        }
        return 0;
    }

    private int assistCount(String tournamentId, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/statistics/assists")
                        .param("tournamentId", tournamentId)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (playerId.equals(node.path("playerId").asText())) {
                return node.path("assists").asInt();
            }
        }
        return 0;
    }

    private int teamGoals(String teamId, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/teams/" + teamId + "/player-stats"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (playerId.equals(node.path("playerId").asText())) {
                return node.path("goals").asInt();
            }
        }
        return 0;
    }

    private void assertGoalsFor(String tournamentId, String teamId, int goalsFor) throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(teamId)).value(goalsFor));
    }

    private int goalCount(String matchId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andReturn();
        int goals = 0;
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (!node.path("voided").asBoolean(false) && "GOAL".equals(node.path("eventType").asText())) {
                goals++;
            }
        }
        return goals;
    }

    private JsonNode eventById(String matchId, String eventId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (eventId.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("event " + eventId + " missing from public protocol");
    }

    private String upsert(String token, String first, String last, int number, String position) throws Exception {
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
                        .content("{\"playerId\":\"%s\"}".formatted(playerId)))
                .andExpect(status().isCreated());
    }

    private String footballSportId(String adminToken) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.path("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("FOOTBALL sport missing");
    }

    private String tournament(String adminToken, String sportId, String name) throws Exception {
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

    private String match(String adminToken, String tournamentId, String homeTeamId, String awayTeamId) throws Exception {
        Instant kickoff = Instant.now().minus(1, ChronoUnit.HOURS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();
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
        return loginOnly(email, password);
    }
}
