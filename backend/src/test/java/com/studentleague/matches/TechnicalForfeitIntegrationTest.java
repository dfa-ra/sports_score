package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TechnicalForfeitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchEventRepository matchEventRepository;

    @Test
    void oneSidedAndMutualForfeitScoreTheTableAndHideEventsUntilCancel() throws Exception {
        String admin = createAdminAndLogin("tf-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Group group = group(admin);
        goal(admin, group.matchId, group.homeTeamId, group.homePlayerId);
        mockMvc.perform(put("/api/v1/admin/matches/" + group.matchId + "/goalkeeper")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","playerId":"%s"}
                                """.formatted(group.homeTeamId, group.homePlayerId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + group.matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + group.matchId + "/finish")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.status").value("FINISHED"));

        assertThat(goalsOf(group, group.homePlayerId)).isEqualTo(1);
        assertThat(cleanSheetsOf(group, group.homePlayerId)).isEqualTo(1);
        row(group, group.homeTeamId)
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(group.homeTeamId)).value(3))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(group.homeTeamId)).value(1));

        mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"away","reason":"other","note":"дождь"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.forfeit").value("away"))
                .andExpect(jsonPath("$.forfeitReason").value("other"))
                .andExpect(jsonPath("$.homeScore").value(5))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andExpect(jsonPath("$.forfeitReasonNote").value("дождь"))
                .andExpect(jsonPath("$.forfeitSetBy.id").exists())
                .andExpect(jsonPath("$.forfeitSetBy.name", containsString("Тест")))
                .andExpect(jsonPath("$.forfeitSetAt").exists());

        mockMvc.perform(get("/api/v1/matches/" + group.matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forfeit").value("away"))
                .andExpect(jsonPath("$.forfeitReason").value("other"))
                .andExpect(jsonPath("$.homeScore").value(5))
                .andExpect(jsonPath("$.forfeitReasonNote").doesNotExist())
                .andExpect(jsonPath("$.forfeitSetBy").doesNotExist())
                .andExpect(jsonPath("$.forfeitSetAt").doesNotExist());
        mockMvc.perform(get("/api/v1/matches/" + group.matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(matchEventRepository.findByMatchIdOrderByTimestampAsc(UUID.fromString(group.matchId))).isNotEmpty();
        assertThat(goalsOf(group, group.homePlayerId)).isZero();
        assertThat(cleanSheetsOf(group, group.homePlayerId)).isZero();

        row(group, group.homeTeamId)
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].played".formatted(group.homeTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].wins".formatted(group.homeTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(group.homeTeamId)).value(3))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(group.homeTeamId)).value(5))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsAgainst".formatted(group.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].result".formatted(group.homeTeamId)).value("W"))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].technical".formatted(group.homeTeamId)).value(false))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].played".formatted(group.awayTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].losses".formatted(group.awayTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(group.awayTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsAgainst".formatted(group.awayTeamId)).value(5))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].result".formatted(group.awayTeamId)).value("L"))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].technical".formatted(group.awayTeamId)).value(true))
                .andExpect(jsonPath("$.tables[0].mutualForfeit").value(false));

        mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"home","reason":"no_show"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit/cancel")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forfeit").value("none"))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(1))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andExpect(jsonPath("$.forfeitSetBy").doesNotExist());

        MvcResult restoredEvents = mockMvc.perform(get("/api/v1/matches/" + group.matchId + "/events"))
                .andExpect(status().isOk())
                .andReturn();
        int restoredGoals = 0;
        for (JsonNode event : objectMapper.readTree(restoredEvents.getResponse().getContentAsString())) {
            if ("GOAL".equals(event.path("eventType").asText())) {
                restoredGoals++;
            }
        }
        assertThat(restoredGoals).isEqualTo(1);
        assertThat(goalsOf(group, group.homePlayerId)).isEqualTo(1);
        assertThat(cleanSheetsOf(group, group.homePlayerId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"both","reason":"incomplete"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forfeit").value("both"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));
        row(group, group.homeTeamId)
                .andExpect(jsonPath("$.tables[0].mutualForfeit").value(true))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(group.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].losses".formatted(group.homeTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].draws".formatted(group.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].wins".formatted(group.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].played".formatted(group.homeTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].goalsFor".formatted(group.homeTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].points".formatted(group.awayTeamId)).value(0))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].losses".formatted(group.awayTeamId)).value(1))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].result".formatted(group.homeTeamId)).value("L"))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].technical".formatted(group.homeTeamId)).value(true))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId == '%s')].form[0].technical".formatted(group.awayTeamId)).value(true));
        assertThat(goalsOf(group, group.homePlayerId)).isZero();
    }

    @Test
    void liveMatchAndSecondForfeitAreRejected() throws Exception {
        String admin = createAdminAndLogin("tf-live-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Group group = group(admin);
        mockMvc.perform(post("/api/v1/referee/matches/" + group.matchId + "/start")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"home","reason":"no_show"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/matches/" + group.matchId))
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.forfeit").value("none"));
    }

    @Test
    void playoffRejectsMutualForfeitAndOneSidedAdvancesTheWinner() throws Exception {
        String admin = createAdminAndLogin("tf-po-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Bracket bracket = open(admin);
        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"both","reason":"no_show"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("В плей-офф выберите одну команду")));
        mockMvc.perform(get("/api/v1/matches/" + bracket.firstMatch))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.forfeit").value("none"));

        mockMvc.perform(post("/api/v1/admin/matches/" + bracket.firstMatch + "/forfeit")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"away","reason":"no_show"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forfeit").value("away"))
                .andExpect(jsonPath("$.homeScore").value(5))
                .andExpect(jsonPath("$.awayScore").value(0))
                .andExpect(jsonPath("$.status").value("FINISHED"));
        mockMvc.perform(get("/api/v1/tournaments/" + bracket.tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[1].slots[0].homeTeamId").value(bracket.homeTeamId));
    }

    @Test
    void awardAndCancelAreLeagueAdminOnly() throws Exception {
        String admin = createAdminAndLogin("tf-auth-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Group group = group(admin);
        String body = """
                {"side":"home","reason":"no_show"}
                """;
        deny(loginAs(Role.REFEREE), group.matchId, body);
        deny(loginAs(Role.CAPTAIN), group.matchId, body);
        deny(loginAs(Role.ANALYST), group.matchId, body);
        deny(registerAndLogin("tf-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!"), group.matchId, body);

        int anonymousAward = mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
        assertThat(anonymousAward).isIn(401, 403);
        int anonymousCancel = mockMvc.perform(post("/api/v1/admin/matches/" + group.matchId + "/forfeit/cancel"))
                .andReturn().getResponse().getStatus();
        assertThat(anonymousCancel).isIn(401, 403);

        mockMvc.perform(get("/api/v1/matches/" + group.matchId))
                .andExpect(jsonPath("$.forfeit").value("none"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    private void deny(String token, String matchId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/forfeit")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/forfeit/cancel")
                        .header("Authorization", auth(token)))
                .andExpect(status().isForbidden());
    }

    private String loginAs(Role role) throws Exception {
        String email = "tf-" + role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        roleService.grantApproved(user, role, null);
        return loginOnly(email, "Str0ngPass!");
    }

    private int goalsOf(Group group, String playerId) throws Exception {
        MvcResult board = mockMvc.perform(get("/api/v1/statistics/scorers")
                        .param("tournamentId", group.tournamentId))
                .andExpect(status().isOk())
                .andReturn();
        int goals = 0;
        for (JsonNode row : objectMapper.readTree(board.getResponse().getContentAsString())) {
            if (playerId.equals(row.get("playerId").asText())) {
                goals = row.get("goals").asInt();
            }
        }
        return goals;
    }

    private int cleanSheetsOf(Group group, String playerId) throws Exception {
        MvcResult board = mockMvc.perform(get("/api/v1/statistics/goalkeepers")
                        .param("tournamentId", group.tournamentId))
                .andExpect(status().isOk())
                .andReturn();
        int sheets = 0;
        for (JsonNode row : objectMapper.readTree(board.getResponse().getContentAsString())) {
            if (playerId.equals(row.get("playerId").asText())) {
                sheets = row.get("cleanSheets").asInt();
            }
        }
        return sheets;
    }

    private org.springframework.test.web.servlet.ResultActions row(Group group, String teamId) throws Exception {
        return mockMvc.perform(get("/api/v1/tournaments/" + group.tournamentId + "/standings"))
                .andExpect(status().isOk());
    }

    private void goal(String admin, String matchId, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":6}
                                """.formatted(teamId, playerId)))
                .andExpect(status().isCreated());
    }

    private Group group(String admin) throws Exception {
        String sportId = footballId(admin);
        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ТП группа","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
        Squad home = register(admin, tournamentId, "Дом");
        Squad away = register(admin, tournamentId, "Гости");
        String matchId = createMatch(admin, tournamentId, home.teamId, away.teamId);
        return new Group(tournamentId, matchId, home.teamId, away.teamId, home.playerId);
    }

    private Bracket open(String admin) throws Exception {
        String sportId = footballId(admin);
        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ТП сетка","sportId":"%s","seasonYear":2026,"format":"GROUPS_PLAYOFF","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
        Squad alpha = register(admin, tournamentId, "Альфа");
        Squad beta = register(admin, tournamentId, "Бета");
        Squad gamma = register(admin, tournamentId, "Гамма");
        Squad delta = register(admin, tournamentId, "Дельта");
        String ids = String.join("\",\"", alpha.teamId, beta.teamId, gamma.teamId, delta.teamId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"Группа A","teamIds":["%s"]}]}
                                """.formatted(ids)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2,3,4]}"))
                .andExpect(status().isOk());
        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pairs":[
                                  {"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"Зал"},
                                  {"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"Зал"}
                                ]}
                                """.formatted(alpha.teamId, beta.teamId, kickoff, gamma.teamId, delta.teamId, kickoff)))
                .andExpect(status().isOk())
                .andReturn();
        String first = objectMapper.readTree(opened.getResponse().getContentAsString())
                .get("rounds").get(0).get("slots").get(0).get("matchId").asText();
        return new Bracket(tournamentId, first, alpha.teamId);
    }

    private String createMatch(String admin, String tournamentId, String home, String away) throws Exception {
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
        String token = registerAndLogin("tf-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Игрок","lastName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isOk());
        String playerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
        String teamName = name + " " + System.nanoTime();
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(teamName, name.substring(0, Math.min(2, name.length())), playerId)))
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

    private record Squad(String teamId, String playerId) {
    }

    private record Group(String tournamentId, String matchId, String homeTeamId, String awayTeamId, String homePlayerId) {
    }

    private record Bracket(String tournamentId, String firstMatch, String homeTeamId) {
    }
}
