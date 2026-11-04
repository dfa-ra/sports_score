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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                .andExpect(jsonPath("$.qualified[0].teamName").value(north.name))
                .andExpect(jsonPath("$.qualified[0].groupName").value("Группа A"))
                .andExpect(jsonPath("$.qualified[0].tableId").value(groupId))
                .andExpect(jsonPath("$.qualified[1].place").value(2))
                .andExpect(jsonPath("$.qualified[1].tableId").value(groupId))
                .andExpect(jsonPath("$.qualified[1].teamId").value(east.teamId))
                .andExpect(jsonPath("$.qualified[1].teamName").value(east.name));
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
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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

        String keptTableId = groupId(admin, tournamentId);
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[0].slots[0].homeTeamId").value(north.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].awayTeamId").value(east.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].venue").value("Манеж"))
                .andExpect(jsonPath("$.qualified.length()").value(2))
                .andExpect(jsonPath("$.qualified[0].teamId").value(north.teamId))
                .andExpect(jsonPath("$.qualified[0].groupName").value("Группа A"))
                .andExpect(jsonPath("$.qualified[0].tableId").value(keptTableId))
                .andExpect(jsonPath("$.qualified[1].teamId").value(east.teamId))
                .andExpect(jsonPath("$.qualified[1].groupName").value("Группа A"))
                .andExpect(jsonPath("$.qualified[1].tableId").value(keptTableId));

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
                .andExpect(jsonPath("$.rounds[1].slots[0].homeWaitingLabel").value("Победитель 1/2 №1"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeWaitingLabel", containsString("№")))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeWaitingLabel", not(containsString("#"))))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayWaitingLabel").value("Победитель 1/2 №2"))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayWaitingLabel", not(containsString("#"))))
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
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamName").value(alpha.name))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeWaitingLabel").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayWaitingLabel").value("Победитель 1/2 №2"));

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
    void voidingAGoalThatWouldDrawAFinishedPlayoffWithoutPenaltiesIsRejected() throws Exception {
        Opened bracket = openSemifinals("po-draw-");
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/start")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        String firstGoal = goal(bracket.admin, bracket.firstMatch, bracket.alpha);
        String secondGoal = goal(bracket.admin, bracket.firstMatch, bracket.alpha);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(2));
        scoreAndFinish(bracket.admin, bracket.secondMatch, bracket.gamma);
        String finalMatch = finalMatchId(bracket.tournamentId);

        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/events/" + firstGoal + "/void")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(bracket.gamma.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].matchId").value(finalMatch));

        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/events/" + secondGoal + "/void")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("После отмены гола будет ничья: сначала задайте серию пенальти"));

        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch + "/events"))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(firstGoal)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(secondGoal)).value(false));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(bracket.gamma.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].matchId").value(finalMatch));
        mockMvc.perform(get("/api/v1/matches/" + finalMatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.awayTeamId").value(bracket.gamma.teamId));
    }

    @Test
    void voidingAGoalBackToADrawIsAllowedWhenAShootoutExists() throws Exception {
        Opened bracket = openSemifinals("po-pen-");
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/start")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        goal(bracket.admin, bracket.firstMatch, bracket.alpha);
        goal(bracket.admin, bracket.firstMatch, bracket.beta);
        decisiveShootout(bracket.admin, bracket.firstMatch, bracket.alpha, bracket.beta);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId));

        String extraGoal = addAdminGoal(bracket.admin, bracket.firstMatch, bracket.alpha);
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.homeScore").value(2))
                .andExpect(jsonPath("$.awayScore").value(1));

        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/events/" + extraGoal + "/void")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch + "/events"))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(extraGoal)).value(true));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyHome").value(4))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyAway").value(3))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId));
    }

    @Test
    void voidingAGoalStillConflictsWhenTheNextMatchHasStarted() throws Exception {
        Opened bracket = openSemifinals("po-v-");
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/start")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        String alphaGoal = goal(bracket.admin, bracket.firstMatch, bracket.alpha);
        goal(bracket.admin, bracket.firstMatch, bracket.beta);
        decisiveShootout(bracket.admin, bracket.firstMatch, bracket.alpha, bracket.beta);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        scoreAndFinish(bracket.admin, bracket.secondMatch, bracket.gamma);
        String finalMatch = finalMatchId(bracket.tournamentId);
        mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/start")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/events/" + alphaGoal + "/void")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("уже начался")))
                .andExpect(jsonPath("$.message", containsString("состав пары")));

        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch + "/events"))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(alphaGoal)).value(false));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(bracket.gamma.teamId));
    }

    @Test
    void groupStageGoalVoidMayLeaveADraw() throws Exception {
        String admin = createAdminAndLogin("po-g-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad home = register(admin, tournamentId, "Север");
        Squad away = register(admin, tournamentId, "Юг");
        String matchId = createGroupMatch(admin, tournamentId, home.teamId, away.teamId);
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        String eventId = goal(admin, matchId, home);
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.bracketRound").value(nullValue()));

        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events/" + eventId + "/void")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andExpect(jsonPath("$.bracketRound").value(nullValue()));
        mockMvc.perform(get("/api/v1/matches/" + matchId + "/events"))
                .andExpect(jsonPath("$[?(@.id == '%s')].voided".formatted(eventId)).value(true));
    }

    @Test
    void wrongPairCountUsesTheRightRussianWord() throws Exception {
        assertPairCount(2, 2, "Нужно назначить 1 пару из вышедших команд");
        assertPairCount(4, 1, "Нужно назначить 2 пары из вышедших команд");
        assertPairCount(8, 1, "Нужно назначить 4 пары из вышедших команд");
        assertPairCount(16, 1, "Нужно назначить 8 пар из вышедших команд");
    }

    @Test
    void advancedMatchWithoutASlotKickoffHasNoDateAndIsNotNext() throws Exception {
        Opened bracket = openSemifinals("po-nd-");
        scoreAndFinish(bracket.admin, bracket.firstMatch, bracket.alpha);
        scoreAndFinish(bracket.admin, bracket.secondMatch, bracket.gamma);
        String finalMatch = finalMatchId(bracket.tournamentId);

        mockMvc.perform(get("/api/v1/matches/" + finalMatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").value(nullValue()));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].matchId").value(finalMatch))
                .andExpect(jsonPath("$.rounds[1].slots[0].scheduledAt").value(nullValue()));
        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", bracket.tournamentId))
                .andExpect(status().isNoContent());
        assertNextIsNot(finalMatch);

        mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/start")
                        .header("Authorization", auth(bracket.admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].scheduledAt").value(nullValue()))
                .andExpect(jsonPath("$.rounds[1].slots[0].matchStatus").value("LIVE"))
                .andExpect(jsonPath("$.rounds[1].slots[0].startedAt", not(nullValue())));
        try {
            mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", bracket.tournamentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(finalMatch))
                    .andExpect(jsonPath("$.status").value("LIVE"))
                    .andExpect(jsonPath("$.scheduledAt").value(nullValue()));
        } finally {
            mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/events")
                            .header("Authorization", auth(bracket.admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":40}
                                    """.formatted(bracket.alpha.teamId, bracket.alpha.playerId)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/finish")
                            .header("Authorization", auth(bracket.admin)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void advancedMatchKeepsTheSlotKickoff() throws Exception {
        Opened bracket = openSemifinals("po-kd-");
        mockMvc.perform(put("/api/v1/tournaments/" + bracket.tournamentId + "/playoff/slots/" + bracket.finalSlotId)
                        .header("Authorization", auth(bracket.admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-01-15T12:00:00Z\",\"venue\":\"Финал-холл\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(nullValue()))
                .andExpect(jsonPath("$.scheduledAt").value("2026-01-15T12:00:00Z"));

        scoreAndFinish(bracket.admin, bracket.firstMatch, bracket.alpha);
        scoreAndFinish(bracket.admin, bracket.secondMatch, bracket.gamma);
        String finalMatch = finalMatchId(bracket.tournamentId);

        mockMvc.perform(get("/api/v1/matches/" + finalMatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").value("2026-01-15T12:00:00Z"))
                .andExpect(jsonPath("$.venue").value("Финал-холл"));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].scheduledAt").value("2026-01-15T12:00:00Z"));
        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", bracket.tournamentId))
                .andExpect(status().isNoContent());
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

    @Test
    void overviewCountsStageAndMatchCardsCarryNames() throws Exception {
        String admin = createAdminAndLogin("po-ov-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Плей-офф"))
                .andExpect(jsonPath("$.format").value("GROUPS_PLAYOFF"))
                .andExpect(jsonPath("$.seasonYear").value(2026))
                .andExpect(jsonPath("$.teamsCount").value(0))
                .andExpect(jsonPath("$.matchesCount").value(0))
                .andExpect(jsonPath("$.stage").value("GROUPS"))
                .andExpect(jsonPath("$.hasPlayoff").value(true))
                .andExpect(jsonPath("$.playoffLive").value(false))
                .andExpect(jsonPath("$.status").value("REGISTRATION"));

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
        String matchId = body.get("rounds").get(0).get("slots").get(0).get("matchId").asText();
        String finalSlotId = body.get("rounds").get(1).get("slots").get(0).get("id").asText();

        mockMvc.perform(get("/api/v1/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeamName").value(alpha.name))
                .andExpect(jsonPath("$.awayTeamName").value(beta.name))
                .andExpect(jsonPath("$.tournamentName").value("Плей-офф"))
                .andExpect(jsonPath("$.minute").value(nullValue()));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')].homeTeamName".formatted(matchId)).value(hasItem(alpha.name)))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].tournamentName".formatted(matchId)).value(hasItem("Плей-офф")));

        Instant finalKick = Instant.parse("2026-11-07T15:00:00Z");
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/slots/" + finalSlotId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"%s\",\"venue\":\"Финал-холл\"}".formatted(finalKick)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(nullValue()))
                .andExpect(jsonPath("$.scheduledAt").value("2026-11-07T15:00:00Z"));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].scheduledAt").value("2026-11-07T15:00:00Z"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeWaitingLabel").value("Победитель 1/2 №1"))
                .andExpect(jsonPath("$.rounds[1].slots[0].minute").value(nullValue()));

        Match live = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        live.setStatus(MatchStatus.LIVE);
        live.setPeriod(1);
        live.setGameTimeSeconds(180);
        live.setClockRunningSince(null);
        matchRepository.saveAndFlush(live);

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[0].slots[0].matchStatus").value("LIVE"))
                .andExpect(jsonPath("$.rounds[0].slots[0].minute").value(3));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.teamsCount").value(4))
                .andExpect(jsonPath("$.matchesCount").value(2))
                .andExpect(jsonPath("$.stage").value("PLAYOFF"))
                .andExpect(jsonPath("$.hasPlayoff").value(true))
                .andExpect(jsonPath("$.playoffLive").value(true))
                .andExpect(jsonPath("$.format").value("GROUPS_PLAYOFF"));

        mockMvc.perform(get("/api/v1/tournaments/" + UUID.randomUUID() + "/overview"))
                .andExpect(status().isNotFound());
    }

    private Opened openSemifinals(String emailPrefix) throws Exception {
        String admin = createAdminAndLogin(emailPrefix + System.nanoTime() + "@example.com", "Str0ngPass!");
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
        return new Opened(
                admin,
                tournamentId,
                alpha,
                beta,
                gamma,
                body.get("rounds").get(0).get("slots").get(0).get("matchId").asText(),
                body.get("rounds").get(0).get("slots").get(1).get("matchId").asText(),
                body.get("rounds").get(1).get("slots").get(0).get("id").asText()
        );
    }

    private String finalMatchId(String tournamentId) throws Exception {
        MvcResult assembled = mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(assembled.getResponse().getContentAsString())
                .get("rounds").get(1).get("slots").get(0).get("matchId").asText();
    }

    private void assertNextIsNot(String matchId) throws Exception {
        MvcResult global = mockMvc.perform(get("/api/v1/matches/next")).andReturn();
        int code = global.getResponse().getStatus();
        if (code == 200) {
            String nextId = objectMapper.readTree(global.getResponse().getContentAsString()).path("id").asText();
            assertNotEquals(matchId, nextId);
            return;
        }
        assertEquals(204, code);
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
        String teamName = name + " " + System.nanoTime();
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(teamName, name.substring(0, 2), playerId)))
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
        return new Squad(teamId, playerId, teamName);
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

    private String addAdminGoal(String admin, String matchId, Squad winner) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":2}
                                """.formatted(winner.teamId, winner.playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private String goal(String admin, String matchId, Squad squad) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":40}
                                """.formatted(squad.teamId, squad.playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private void decisiveShootout(String admin, String matchId, Squad home, Squad away) throws Exception {
        boolean[] homeScored = {true, true, true, true, false};
        boolean[] awayScored = {true, true, false, true, false};
        for (int round = 0; round < homeScored.length; round++) {
            kick(admin, matchId, home.teamId, homeScored[round]);
            kick(admin, matchId, away.teamId, awayScored[round]);
        }
    }

    private void kick(String admin, String matchId, String teamId, boolean scored) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/shootout")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"scored\":%s}".formatted(teamId, scored)))
                .andExpect(status().isCreated());
    }

    private String createGroupMatch(String admin, String tournamentId, String home, String away) throws Exception {
        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        MvcResult created = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, home, away, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private void assertPairCount(int teams, int submittedPairs, String message) throws Exception {
        String admin = createAdminAndLogin("po-c-" + teams + "-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        String[] ids = new String[teams];
        for (int index = 0; index < teams; index++) {
            ids[index] = register(admin, tournamentId, "К" + (index + 1)).teamId;
        }
        join(admin, tournamentId, "Группа A", ids);
        StringBuilder places = new StringBuilder();
        for (int place = 1; place <= teams; place++) {
            if (place > 1) {
                places.append(',');
            }
            places.append(place);
        }
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[" + places + "]}"))
                .andExpect(status().isOk());
        String[] pairs = new String[submittedPairs];
        for (int index = 0; index < submittedPairs; index++) {
            pairs[index] = pair(ids[0], ids[1], "Зал");
        }
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(pairs)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
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

    private record Squad(String teamId, String playerId, String name) {
    }

    private record Opened(
            String admin,
            String tournamentId,
            Squad alpha,
            Squad beta,
            Squad gamma,
            String firstMatch,
            String secondMatch,
            String finalSlotId
    ) {
    }
}
