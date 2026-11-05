package com.studentleague.home;

import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeOverviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void overviewCarriesRunningTournamentsMatchesAndScorers() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("home-admin-" + stamp + "@example.com", "Str0ngPass!");
        String captainEmail = "home-cap-" + stamp + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Илья","lastName":"Орлов"}
                                """))
                .andExpect(status().isOk());
        String homePlayer = playerId(captainToken);
        String teamA = createTeam(adminToken, "Пумы " + stamp, homePlayer);
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "home-other-" + stamp + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Петр","lastName":"Волков"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Физтех " + stamp, playerId(otherToken));

        String sport = sportId(adminToken);
        String alpha = createTournament(adminToken, sport, "Лига ИТМО по футзалу 2099", "REGISTRATION", "2026-09-01");
        String beta = createTournament(adminToken, sport, "Осенняя лига факультетов", "REGISTRATION", "2026-10-01");
        String signup = createTournament(adminToken, sport, "Суперкубок ИТМО по фиджитал-футболу", "REGISTRATION", "2099-11-10");
        registerAndApprove(adminToken, captainToken, alpha, teamA);
        registerAndApprove(adminToken, otherToken, alpha, teamB);
        registerAndApprove(adminToken, captainToken, beta, teamA);
        registerAndApprove(adminToken, otherToken, beta, teamB);
        registerAndApprove(adminToken, captainToken, signup, teamA);
        activate(adminToken, alpha);
        activate(adminToken, beta);

        Instant past = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant soon = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = soon.plus(1, ChronoUnit.DAYS);
        String alphaDone = createMatch(adminToken, alpha, teamA, teamB, past);
        String alphaNext = createMatch(adminToken, alpha, teamA, teamB, soon);
        String betaDone = createMatch(adminToken, beta, teamB, teamA, past.minus(1, ChronoUnit.DAYS));
        String betaNext = createMatch(adminToken, beta, teamB, teamA, later);
        finish(adminToken, alphaDone);
        finish(adminToken, betaDone);
        mockMvc.perform(post("/api/v1/admin/matches/" + alphaDone + "/events")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":8}
                                """.formatted(teamA, homePlayer)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/home/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.next.id").value(alphaNext))
                .andExpect(jsonPath("$.next.tournamentShortName").value("Лига ИТМО"))
                .andExpect(jsonPath("$.next.homeTeamName").value("Пумы " + stamp))
                .andExpect(jsonPath("$.next.awayTeamName").value("Физтех " + stamp))
                .andExpect(jsonPath("$.upcoming[0].id").value(alphaNext))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].shortName".formatted(alpha), hasItem("Лига ИТМО")))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].teamsCount".formatted(alpha), hasItem(2)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].leader.teamName".formatted(alpha), hasItem("Пумы " + stamp)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].leader.points".formatted(alpha), hasItem(3)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].stageLabel".formatted(alpha), hasItem("Тур 2 из 2")))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].currentRound".formatted(alpha), hasItem(2)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].roundsTotal".formatted(alpha), hasItem(2)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].lastMatch.id".formatted(alpha), hasItem(alphaDone)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].nextMatch.id".formatted(alpha), hasItem(alphaNext)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].shortName".formatted(beta), hasItem("Осенняя лига")))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].registrationClosesOn".formatted(signup), hasItem("2099-11-10")))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].teamsCount".formatted(signup), hasItem(1)))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].leader".formatted(signup), everyItem(nullValue())))
                .andExpect(jsonPath("$.tournaments[?(@.id == '%s')].progress".formatted(signup), everyItem(nullValue())))
                .andExpect(jsonPath("$.scorersByTournament[?(@.tournamentId == '%s')].shortName".formatted(alpha), hasItem("Лига ИТМО")))
                .andExpect(jsonPath("$.scorersByTournament[?(@.tournamentId == '%s')]".formatted(signup)).isEmpty())
                .andExpect(jsonPath("$.summary.matchesPlayed").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.summary.goals").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.summary.goalsPerMatch").value(notNullValue()))
                .andExpect(jsonPath("$.summary.teams").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.summary.players").value(greaterThanOrEqualTo(1)));

        MvcResult body = mockMvc.perform(get("/api/v1/home/overview"))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(body.getResponse().getContentAsString(StandardCharsets.UTF_8));
        var scorers = root.get("scorersByTournament");
        boolean scorerFound = false;
        for (var group : scorers) {
            if (!alpha.equals(group.get("tournamentId").asText())) {
                continue;
            }
            var row = group.get("scorers").get(0);
            org.junit.jupiter.api.Assertions.assertEquals(1, row.get("goals").asInt());
            org.junit.jupiter.api.Assertions.assertEquals("Пумы " + stamp, row.get("teamName").asText());
            org.junit.jupiter.api.Assertions.assertEquals("Илья", row.get("firstName").asText());
            org.junit.jupiter.api.Assertions.assertEquals("Орлов", row.get("lastName").asText());
            scorerFound = true;
        }
        org.junit.jupiter.api.Assertions.assertTrue(scorerFound, "alpha scorers missing");
        var recent = root.get("recent");
        int alphaAt = -1;
        int betaAt = -1;
        for (int i = 0; i < recent.size(); i++) {
            String id = recent.get(i).get("id").asText();
            if (alphaDone.equals(id)) {
                alphaAt = i;
            }
            if (betaDone.equals(id)) {
                betaAt = i;
            }
            if (i < 2) {
                String tournamentId = recent.get(i).get("tournamentId").asText();
                String other = recent.get(i == 0 ? 1 : 0).get("tournamentId").asText();
                if (alpha.equals(tournamentId) || beta.equals(tournamentId)) {
                    org.junit.jupiter.api.Assertions.assertNotEquals(tournamentId, other);
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(alphaAt >= 0, "alpha result missing");
        org.junit.jupiter.api.Assertions.assertTrue(betaAt >= 0, "beta result missing");
        org.junit.jupiter.api.Assertions.assertTrue(betaAt < alphaAt, "the later final whistle leads the recent list");

        mockMvc.perform(get("/api/v1/statistics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesPlayed").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.goalsPerMatch").exists());
        mockMvc.perform(get("/api/v1/statistics/summary").param("tournamentId", alpha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesPlayed").value(1))
                .andExpect(jsonPath("$.goals").value(1))
                .andExpect(jsonPath("$.teams").value(2));

        mockMvc.perform(get("/api/v1/home/overview"))
                .andExpect(jsonPath("$.upcoming[?(@.id == '%s')]".formatted(betaNext)).isNotEmpty())
                .andExpect(jsonPath("$.recent[?(@.id == '%s')].tournamentName".formatted(betaDone), hasItem("Осенняя лига факультетов")))
                .andExpect(jsonPath("$.recent[?(@.id == '%s')].homeTeamName".formatted(betaDone), hasItem("Физтех " + stamp)));
    }

    @Test
    void nextIsTheEarliestFutureScheduledMatchAcrossActiveTournaments() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("near-admin-" + stamp + "@example.com", "Str0ngPass!");
        String captainEmail = "near-cap-" + stamp + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Илья","lastName":"Орлов"}
                                """))
                .andExpect(status().isOk());
        String homePlayer = playerId(captainToken);
        String teamA = createTeam(adminToken, "Север " + stamp, homePlayer);
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "near-other-" + stamp + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Петр","lastName":"Волков"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Юг " + stamp, playerId(otherToken));

        String sport = sportId(adminToken);
        String futureCup = createTournament(adminToken, sport, "Будущий кубок " + stamp, "REGISTRATION", "2026-09-01");
        String liveCup = createTournament(adminToken, sport, "Живой кубок " + stamp, "REGISTRATION", "2026-09-02");
        String pastCup = createTournament(adminToken, sport, "Прошлый кубок " + stamp, "REGISTRATION", "2026-09-03");
        String signup = createTournament(adminToken, sport, "Набор кубка " + stamp, "REGISTRATION", "2099-01-01");
        registerAndApprove(adminToken, captainToken, futureCup, teamA);
        registerAndApprove(adminToken, otherToken, futureCup, teamB);
        registerAndApprove(adminToken, captainToken, liveCup, teamA);
        registerAndApprove(adminToken, otherToken, liveCup, teamB);
        registerAndApprove(adminToken, captainToken, pastCup, teamA);
        registerAndApprove(adminToken, otherToken, pastCup, teamB);
        registerAndApprove(adminToken, captainToken, signup, teamA);
        registerAndApprove(adminToken, otherToken, signup, teamB);
        activate(adminToken, futureCup);
        activate(adminToken, liveCup);
        activate(adminToken, pastCup);

        Instant kickoff = Instant.now().plus(2, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Instant pastKick = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant signupSooner = kickoff.minus(1, ChronoUnit.HOURS);
        String futureMatch = createMatch(adminToken, futureCup, teamA, teamB, kickoff);
        String liveMatch = createMatch(adminToken, liveCup, teamB, teamA, kickoff);
        String pastMatch = createMatch(adminToken, pastCup, teamA, teamB, pastKick);
        String undatedMatch = createMatch(adminToken, pastCup, teamB, teamA, kickoff.plus(1, ChronoUnit.DAYS));
        String signupMatch = createMatch(adminToken, signup, teamA, teamB, signupSooner);
        var undated = matchRepository.findById(UUID.fromString(undatedMatch)).orElseThrow();
        undated.setScheduledAt(null);
        matchRepository.saveAndFlush(undated);
        boolean started = false;
        try {
            start(adminToken, liveMatch);
            started = true;
            mockMvc.perform(get("/api/v1/home/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.live[?(@.id == '%s')]".formatted(liveMatch)).isNotEmpty())
                    .andExpect(jsonPath("$.live[?(@.id == '%s')].status".formatted(liveMatch), hasItem("LIVE")))
                    .andExpect(jsonPath("$.next.id").value(futureMatch))
                    .andExpect(jsonPath("$.next.status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.next.tournamentId").value(futureCup))
                    .andExpect(jsonPath("$.next.id").value(org.hamcrest.Matchers.not(liveMatch)))
                    .andExpect(jsonPath("$.live[?(@.id == '%s')]".formatted(futureMatch)).isEmpty());
        } finally {
            if (started) {
                finishLive(adminToken, liveMatch);
            }
            for (String matchId : new String[] {futureMatch, pastMatch, undatedMatch, signupMatch}) {
                mockMvc.perform(delete("/api/v1/matches/" + matchId)
                                .header("Authorization", auth(adminToken)))
                        .andExpect(status().isNoContent());
            }
        }
    }

    private void start(String adminToken, String matchId) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private void finishLive(String adminToken, String matchId) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private void activate(String adminToken, String tournamentId) throws Exception {
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }

    private void finish(String adminToken, String matchId) throws Exception {
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + matchId + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private String createMatch(String adminToken, String tournamentId, String homeId, String awayId, Instant kickoff) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeId, awayId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
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

    private String createTournament(String adminToken, String sportId, String name, String status, String startDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2099,"format":"ROUND_ROBIN","status":"%s","startDate":"%s"}
                                """.formatted(name, sportId, status, startDate)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String sportId(String adminToken) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, Math.min(8, name.length())), captainPlayerId)))
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
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
