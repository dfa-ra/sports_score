package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayoffIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void qualificationUsesConfiguredPlacesAndKeepsFinishedScores() throws Exception {
        String admin = createAdminAndLogin("po-q-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad north = register(admin, tournamentId, "Север");
        Squad west = register(admin, tournamentId, "Запад");
        Squad east = register(admin, tournamentId, "Восток");
        join(admin, tournamentId, "Группа A", north.teamId, west.teamId, east.teamId);

        UUID kept = finish(tournamentId, sportId, north.teamId, west.teamId, 2, 0);
        finish(tournamentId, sportId, north.teamId, east.teamId, 1, 0);
        finish(tournamentId, sportId, west.teamId, east.teamId, 0, 0);

        String groupId = groupId(admin, tournamentId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/matches/" + kept + "/group")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"%s\"}".formatted(groupId)))
                .andExpect(status().isOk());

        Match stored = matchRepository.findById(kept).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(2, stored.getHomeScore());
        org.junit.jupiter.api.Assertions.assertEquals(0, stored.getAwayScore());
        org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(north.teamId), stored.getHomeTeamId());
        org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(west.teamId), stored.getAwayTeamId());
        org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(groupId), stored.getTableId());
        org.junit.jupiter.api.Assertions.assertEquals(MatchStatus.FINISHED, stored.getStatus());

        mockMvc.perform(get("/api/v1/matches/" + kept))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(groupId))
                .andExpect(jsonPath("$.homeScore").value(2))
                .andExpect(jsonPath("$.status").value("FINISHED"));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advancingPlaces[0]").value(1))
                .andExpect(jsonPath("$.advancingPlaces[1]").value(2));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualified.length()").value(2))
                .andExpect(jsonPath("$.qualified[0].place").value(1))
                .andExpect(jsonPath("$.qualified[0].teamId").value(north.teamId))
                .andExpect(jsonPath("$.qualified[0].teamName").value("Север"))
                .andExpect(jsonPath("$.qualified[0].groupName").value("Группа A"))
                .andExpect(jsonPath("$.qualified[1].place").value(2))
                .andExpect(jsonPath("$.qualified[1].teamId").value(east.teamId))
                .andExpect(jsonPath("$.qualified[1].teamName").value("Восток"));
    }

    @Test
    void refusesFieldThatIsNotPowerOfTwo() throws Exception {
        String admin = createAdminAndLogin("po-n-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad a = register(admin, tournamentId, "Альфа");
        Squad b = register(admin, tournamentId, "Бета");
        Squad c = register(admin, tournamentId, "Гамма");
        join(admin, tournamentId, "Группа A", a.teamId, b.teamId, c.teamId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2,3]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(pair(a.teamId, b.teamId, "Зал"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("3")))
                .andExpect(jsonPath("$.message", containsString("без автоматических пропусков")));
    }

    @Test
    void pairingRejectsTeamOutsideConfiguredPlaces() throws Exception {
        String admin = createAdminAndLogin("po-p-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad north = register(admin, tournamentId, "Север");
        Squad west = register(admin, tournamentId, "Запад");
        Squad east = register(admin, tournamentId, "Восток");
        join(admin, tournamentId, "Группа A", north.teamId, west.teamId, east.teamId);
        finish(tournamentId, sportId, north.teamId, west.teamId, 2, 0);
        finish(tournamentId, sportId, north.teamId, east.teamId, 1, 0);
        finish(tournamentId, sportId, west.teamId, east.teamId, 0, 0);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(pair(north.teamId, west.teamId, "Зал"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("выбранных мест")));
    }

    @Test
    void teamsStayLockedAfterBracketIsCreated() throws Exception {
        String admin = createAdminAndLogin("po-l-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad north = register(admin, tournamentId, "Север");
        Squad west = register(admin, tournamentId, "Запад");
        Squad east = register(admin, tournamentId, "Восток");
        join(admin, tournamentId, "Группа A", north.teamId, west.teamId, east.teamId);
        finish(tournamentId, sportId, north.teamId, west.teamId, 2, 0);
        finish(tournamentId, sportId, north.teamId, east.teamId, 1, 0);
        finish(tournamentId, sportId, west.teamId, east.teamId, 0, 0);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isOk());

        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(pair(north.teamId, east.teamId, "Манеж"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionStage").value("PLAYOFF"))
                .andExpect(jsonPath("$.rounds[0].slots[0].homeTeamId").value(north.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].awayTeamId").value(east.teamId))
                .andReturn();
        String slotId = objectMapper.readTree(opened.getResponse().getContentAsString())
                .get("rounds").get(0).get("slots").get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/slots/" + slotId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"homeTeamId":"%s","venue":"Другой зал"}
                                """.formatted(west.teamId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("нельзя")));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[0].slots[0].homeTeamId").value(north.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].awayTeamId").value(east.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].venue").value("Манеж"));

        Instant later = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/slots/" + slotId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","venue":"Зал ИТМО"}
                                """.formatted(later)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeamId").value(north.teamId))
                .andExpect(jsonPath("$.awayTeamId").value(east.teamId))
                .andExpect(jsonPath("$.venue").value("Зал ИТМО"));
    }

    @Test
    void winnerAdvancesIntoTheNextSlot() throws Exception {
        String admin = createAdminAndLogin("po-w-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad alpha = register(admin, tournamentId, "Альфа");
        Squad beta = register(admin, tournamentId, "Бета");
        Squad gamma = register(admin, tournamentId, "Гамма");
        Squad delta = register(admin, tournamentId, "Дельта");
        join(admin, tournamentId, "Группа A", alpha.teamId, beta.teamId, gamma.teamId, delta.teamId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2,3,4]}"))
                .andExpect(status().isOk());

        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(
                                pair(alpha.teamId, beta.teamId, "Зал 1"),
                                pair(gamma.teamId, delta.teamId, "Зал 2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds.length()").value(2))
                .andExpect(jsonPath("$.rounds[1].title").value("Финал"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayWaitingLabel", containsString("Ожидает")))
                .andReturn();
        JsonNode body = objectMapper.readTree(opened.getResponse().getContentAsString());
        String matchId = body.get("rounds").get(0).get("slots").get(0).get("matchId").asText();
        String finalSlotId = body.get("rounds").get(1).get("slots").get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":30}
                                """.formatted(alpha.teamId, alpha.playerId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamName").value("Альфа"))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayWaitingLabel", containsString("Ожидает")));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/slots/" + finalSlotId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"awayTeamId\":\"%s\"}".formatted(beta.teamId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(alpha.teamId));
    }

    @Test
    void adminVoidRecomputesOpenSlotAndRejectsStartedNext() throws Exception {
        String admin = createAdminAndLogin("po-v-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad alpha = register(admin, tournamentId, "Альфа");
        Squad beta = register(admin, tournamentId, "Бета");
        Squad gamma = register(admin, tournamentId, "Гамма");
        Squad delta = register(admin, tournamentId, "Дельта");
        join(admin, tournamentId, "Группа A", alpha.teamId, beta.teamId, gamma.teamId, delta.teamId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2,3,4]}"))
                .andExpect(status().isOk());
        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(
                                pair(alpha.teamId, beta.teamId, "Зал 1"),
                                pair(gamma.teamId, delta.teamId, "Зал 2"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(opened.getResponse().getContentAsString());
        String firstMatch = body.get("rounds").get(0).get("slots").get(0).get("matchId").asText();
        String secondMatch = body.get("rounds").get(0).get("slots").get(1).get("matchId").asText();

        String eventId = scoreAndFinish(admin, firstMatch, alpha);
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(alpha.teamId));

        mockMvc.perform(post("/api/v1/admin/matches/" + firstMatch + "/events/" + eventId + "/void")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(nullValue()));

        addAdminGoal(admin, firstMatch, alpha);
        scoreAndFinish(admin, secondMatch, gamma);
        MvcResult assembled = mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(gamma.teamId))
                .andReturn();
        String finalMatch = objectMapper.readTree(assembled.getResponse().getContentAsString())
                .get("rounds").get(1).get("slots").get(0).get("matchId").asText();
        mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());

        String blockingGoal = goalEventId(firstMatch);
        mockMvc.perform(post("/api/v1/admin/matches/" + firstMatch + "/events/" + blockingGoal + "/void")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("уже начался")))
                .andExpect(jsonPath("$.message", containsString("состав пары")));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(gamma.teamId));
        mockMvc.perform(get("/api/v1/matches/" + firstMatch))
                .andExpect(jsonPath("$.homeScore").value(1));
    }

    @Test
    void nonAdminCannotManagePlayoff() throws Exception {
        String admin = createAdminAndLogin("po-a-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fan = registerAndLogin("po-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        String matchId = UUID.randomUUID().toString();
        String tableId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(pair(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Зал"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/matches/" + matchId + "/group")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"%s\"}".formatted(tableId)))
                .andExpect(status().isForbidden());
    }

    private String footballId(String admin) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("FOOTBALL sport is missing");
    }

    private String createTournament(String admin, String sportId) throws Exception {
        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Плей-офф","sportId":"%s","seasonYear":2026,"format":"GROUPS_PLAYOFF","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
    }

    private void join(String admin, String tournamentId, String groupName, String... teamIds) throws Exception {
        String ids = String.join("\",\"", teamIds);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"%s","teamIds":["%s"]}]}
                                """.formatted(groupName, ids)))
                .andExpect(status().isOk());
    }

    private String groupId(String admin, String tournamentId) throws Exception {
        MvcResult tables = mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(tables.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private UUID finish(String tournamentId, String sportId, String home, String away, int homeScore, int awayScore) {
        Match match = new Match();
        match.setTournamentId(UUID.fromString(tournamentId));
        match.setSportId(UUID.fromString(sportId));
        match.setHomeTeamId(UUID.fromString(home));
        match.setAwayTeamId(UUID.fromString(away));
        match.setScheduledAt(Instant.now());
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return matchRepository.save(match).getId();
    }

    private Squad register(String admin, String tournamentId, String name) throws Exception {
        String email = "po-" + System.nanoTime() + "@example.com";
        String token = registerAndLogin(email, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Игрок","lastName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isOk());
        MvcResult me = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        String playerId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, 2), playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = objectMapper.readTree(team.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        return new Squad(teamId, playerId);
    }

    private String scoreAndFinish(String admin, String matchId, Squad winner) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":40}
                                """.formatted(winner.teamId, winner.playerId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1));
        return goalEventId(matchId);
    }

    private void addAdminGoal(String admin, String matchId, Squad winner) throws Exception {
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":2}
                                """.formatted(winner.teamId, winner.playerId)))
                .andExpect(status().isCreated());
    }

    private String goalEventId(String matchId) throws Exception {
        MvcResult events = mockMvc.perform(get("/api/v1/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode event : objectMapper.readTree(events.getResponse().getContentAsString())) {
            if ("GOAL".equals(event.get("eventType").asText()) && !event.get("voided").asBoolean()) {
                return event.get("id").asText();
            }
        }
        throw new IllegalStateException("active goal not found");
    }

    private static String pair(String home, String away, String venue) {
        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        return """
                {"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"%s"}
                """.formatted(home, away, kickoff, venue);
    }

    private static String pairsJson(String... pairs) {
        return "{\"pairs\":[" + String.join(",", pairs) + "]}";
    }

    private record Squad(String teamId, String playerId) {
    }
}
