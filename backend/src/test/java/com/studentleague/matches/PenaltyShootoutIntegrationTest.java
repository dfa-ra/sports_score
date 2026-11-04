package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PenaltyShootoutIntegrationTest extends AbstractIntegrationTest {

    private static final boolean[] HOME_FOUR = {true, true, true, true, false};
    private static final boolean[] AWAY_THREE = {true, true, false, true, false};
    // 3:4 only after both have taken the fifth kick. Away's last goal is the one that decides it.
    private static final boolean[] HOME_THREE = {true, true, true, false, false};
    private static final boolean[] AWAY_FOUR = {true, true, false, true, true};

    @Test
    void drawCannotFinishWithoutAShootout() throws Exception {
        String admin = admin();
        Bracket bracket = open(admin);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("без серии пенальти")));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andExpect(jsonPath("$.penaltyHome").value(nullValue()));
    }

    @Test
    void tiedShootoutCannotBeClosed() throws Exception {
        String admin = admin();
        Bracket bracket = open(admin);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        kick(admin, bracket.firstMatch, bracket.alpha.teamId, true);
        kick(admin, bracket.firstMatch, bracket.beta.teamId, true);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("не может закончиться вничью")));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.penaltyHome").value(1))
                .andExpect(jsonPath("$.penaltyAway").value(1));
    }

    @Test
    void fourThreeShootoutAdvancesWinnerAndKeepsScoreAndScorers() throws Exception {
        String admin = admin();
        Bracket bracket = open(admin);
        level(admin, bracket.firstMatch, bracket.alpha, bracket.beta);
        series(admin, bracket.firstMatch, bracket.alpha.teamId, bracket.beta.teamId, HOME_FOUR, AWAY_THREE);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1))
                .andExpect(jsonPath("$.penaltyHome").value(4))
                .andExpect(jsonPath("$.penaltyAway").value(3));

        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch + "/events"))
                .andExpect(jsonPath("$.[?(@.eventType == 'GOAL')]").value(org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/api/v1/statistics/players")
                        .param("tournamentId", bracket.tournamentId)
                        .param("playerId", bracket.alpha.playerId))
                .andExpect(jsonPath("$[0].goals").value(1));
        mockMvc.perform(get("/api/v1/statistics/players")
                        .param("tournamentId", bracket.tournamentId)
                        .param("playerId", bracket.beta.playerId))
                .andExpect(jsonPath("$[0].goals").value(1));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[0].slots[0].homeScore").value(1))
                .andExpect(jsonPath("$.rounds[0].slots[0].awayScore").value(1))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyHome").value(4))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyAway").value(3))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId));
    }

    @Test
    void groupMatchCanStillDraw() throws Exception {
        String admin = admin();
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad home = register(admin, tournamentId, "Север");
        Squad away = register(admin, tournamentId, "Юг");
        String matchId = createGroupMatch(admin, tournamentId, home.teamId, away.teamId);
        level(admin, matchId, home, away);
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1))
                .andExpect(jsonPath("$.bracketRound").value(nullValue()))
                .andExpect(jsonPath("$.penaltyHome").value(nullValue()))
                .andExpect(jsonPath("$.penaltyAway").value(nullValue()));
    }

    @Test
    void correctingShootoutMovesWinnerWhenNextMatchHasNotStarted() throws Exception {
        String admin = admin();
        Bracket bracket = open(admin);
        level(admin, bracket.firstMatch, bracket.alpha, bracket.beta);
        series(admin, bracket.firstMatch, bracket.alpha.teamId, bracket.beta.teamId, HOME_FOUR, AWAY_THREE);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        scoreAndFinish(admin, bracket.secondMatch, bracket.gamma);

        MvcResult assembled = mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(bracket.gamma.teamId))
                .andReturn();
        String finalMatch = objectMapper.readTree(assembled.getResponse().getContentAsString())
                .get("rounds").get(1).get("slots").get(0).get("matchId").asText();

        mockMvc.perform(put("/api/v1/admin/matches/" + bracket.firstMatch + "/shootout")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replaceBody(bracket.alpha.teamId, bracket.beta.teamId, HOME_THREE, AWAY_FOUR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyHome").value(3))
                .andExpect(jsonPath("$.penaltyAway").value(4))
                .andExpect(jsonPath("$.decisive").value(true));

        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(1))
                .andExpect(jsonPath("$.penaltyHome").value(3))
                .andExpect(jsonPath("$.penaltyAway").value(4));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.beta.teamId))
                .andExpect(jsonPath("$.rounds[1].slots[0].awayTeamId").value(bracket.gamma.teamId));
        mockMvc.perform(get("/api/v1/matches/" + finalMatch))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.homeTeamId").value(bracket.beta.teamId))
                .andExpect(jsonPath("$.awayTeamId").value(bracket.gamma.teamId));
    }

    @Test
    void correctingShootoutRejectsWhenNextMatchHasStarted() throws Exception {
        String admin = admin();
        Bracket bracket = open(admin);
        level(admin, bracket.firstMatch, bracket.alpha, bracket.beta);
        series(admin, bracket.firstMatch, bracket.alpha.teamId, bracket.beta.teamId, HOME_FOUR, AWAY_THREE);
        mockMvc.perform(post("/api/v1/referee/matches/" + bracket.firstMatch + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        scoreAndFinish(admin, bracket.secondMatch, bracket.gamma);
        MvcResult assembled = mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andReturn();
        String finalMatch = objectMapper.readTree(assembled.getResponse().getContentAsString())
                .get("rounds").get(1).get("slots").get(0).get("matchId").asText();
        mockMvc.perform(post("/api/v1/referee/matches/" + finalMatch + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/matches/" + bracket.firstMatch + "/shootout")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replaceBody(bracket.alpha.teamId, bracket.beta.teamId, HOME_THREE, AWAY_FOUR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("уже начался")))
                .andExpect(jsonPath("$.message", containsString("состав пары")));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.alpha.teamId))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyHome").value(4))
                .andExpect(jsonPath("$.rounds[0].slots[0].penaltyAway").value(3));
    }

    private void level(String admin, String matchId, Squad home, Squad away) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        goal(admin, matchId, home);
        goal(admin, matchId, away);
    }

    private void goal(String admin, String matchId, Squad squad) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","gameTime":40}
                                """.formatted(squad.teamId, squad.playerId)))
                .andExpect(status().isCreated());
    }

    private void scoreAndFinish(String admin, String matchId, Squad winner) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        goal(admin, matchId, winner);
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1));
    }

    private void series(
            String admin,
            String matchId,
            String home,
            String away,
            boolean[] homeScored,
            boolean[] awayScored
    ) throws Exception {
        for (int round = 0; round < homeScored.length; round++) {
            kick(admin, matchId, home, homeScored[round]);
            kick(admin, matchId, away, awayScored[round]);
        }
    }

    private void kick(String admin, String matchId, String teamId, boolean scored) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/shootout")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"scored\":%s}".formatted(teamId, scored)))
                .andExpect(status().isCreated());
    }

    private static String replaceBody(String home, String away, boolean[] homeScored, boolean[] awayScored) {
        StringBuilder body = new StringBuilder("{\"kicks\":[");
        for (int round = 0; round < homeScored.length; round++) {
            if (round > 0) {
                body.append(',');
            }
            body.append("{\"teamId\":\"").append(home).append("\",\"scored\":").append(homeScored[round]).append('}');
            body.append(",{\"teamId\":\"").append(away).append("\",\"scored\":").append(awayScored[round]).append('}');
        }
        body.append("]}");
        return body.toString();
    }

    private String admin() throws Exception {
        return createAdminAndLogin("pen-" + System.nanoTime() + "@example.com", "Str0ngPass!");
    }

    private Bracket open(String admin) throws Exception {
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        Squad alpha = register(admin, tournamentId, "Альфа");
        Squad beta = register(admin, tournamentId, "Бета");
        Squad gamma = register(admin, tournamentId, "Гамма");
        Squad delta = register(admin, tournamentId, "Дельта");
        join(admin, tournamentId, alpha.teamId, beta.teamId, gamma.teamId, delta.teamId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2,3,4]}"))
                .andExpect(status().isOk());
        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pairsJson(
                                pair(alpha.teamId, beta.teamId),
                                pair(gamma.teamId, delta.teamId))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rounds = objectMapper.readTree(opened.getResponse().getContentAsString()).get("rounds");
        String first = rounds.get(0).get("slots").get(0).get("matchId").asText();
        String second = rounds.get(0).get("slots").get(1).get("matchId").asText();
        return new Bracket(tournamentId, first, second, alpha, beta, gamma, delta);
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
                                {"name":"Пенальти","sportId":"%s","seasonYear":2026,"format":"GROUPS_PLAYOFF","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
    }

    private void join(String admin, String tournamentId, String... teamIds) throws Exception {
        String ids = String.join("\",\"", teamIds);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"Группа A","teamIds":["%s"]}]}
                                """.formatted(ids)))
                .andExpect(status().isOk());
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

    private Squad register(String admin, String tournamentId, String name) throws Exception {
        String token = registerAndLogin("pen-" + System.nanoTime() + "@example.com", "Str0ngPass!");
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
        return new Squad(teamId, playerId);
    }

    private static String pair(String home, String away) {
        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        return """
                {"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"Зал"}
                """.formatted(home, away, kickoff);
    }

    private static String pairsJson(String... pairs) {
        return "{\"pairs\":[" + String.join(",", pairs) + "]}";
    }

    private record Squad(String teamId, String playerId) {
    }

    private record Bracket(
            String tournamentId,
            String firstMatch,
            String secondMatch,
            Squad alpha,
            Squad beta,
            Squad gamma,
            Squad delta
    ) {
    }
}
