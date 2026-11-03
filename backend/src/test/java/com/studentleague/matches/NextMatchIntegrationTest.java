package com.studentleague.matches;

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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NextMatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void nextMatchIsTheSoonestKickoffNotAnEarlierScheduledRow() throws Exception {
        String adminToken = createAdminAndLogin("next-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "next-cap-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Нора","lastName":"Ближняя"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "Soon Home", playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "next-other-" + System.nanoTime() + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Петр","lastName":"Дальний"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Soon Away", playerId(otherToken));
        otherToken = reLogin(otherEmail, "Str0ngPass!");

        String tournamentId = createTournament(adminToken, sportId(adminToken));
        registerAndApprove(adminToken, captainToken, tournamentId, teamA);
        registerAndApprove(adminToken, otherToken, tournamentId, teamB);

        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant soon = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = soon.plus(3, ChronoUnit.DAYS);
        String laterId = createMatch(adminToken, tournamentId, teamA, teamB, later);
        String soonId = createMatch(adminToken, tournamentId, teamA, teamB, soon);
        String pastId = createMatch(adminToken, tournamentId, teamA, teamB, past);
        // The suite shares one database. A live match left by another test is the real next match.
        // This fixture is three scheduled rows and nothing in play.
        retireOtherOpenMatches(laterId, soonId, pastId);

        mockMvc.perform(get("/api/v1/matches/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(soonId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void finishedMatchWithAFutureKickoffIsNotNearest() throws Exception {
        String adminToken = createAdminAndLogin("next-fin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "next-fin-cap-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Нина","lastName":"Финиш"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "Fin Home", playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "next-fin-other-" + System.nanoTime() + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Глеб","lastName":"Финиш"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Fin Away", playerId(otherToken));

        String tournamentId = createTournament(adminToken, sportId(adminToken));
        registerAndApprove(adminToken, captainToken, tournamentId, teamA);
        registerAndApprove(adminToken, otherToken, tournamentId, teamB);

        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String finishedId = createMatch(adminToken, tournamentId, teamA, teamB, future);
        mockMvc.perform(post("/api/v1/referee/matches/" + finishedId + "/start")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/referee/matches/" + finishedId + "/finish")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", tournamentId))
                .andExpect(status().isNoContent());

        Instant later = future.plus(2, ChronoUnit.DAYS);
        String scheduledId = createMatch(adminToken, tournamentId, teamA, teamB, later);
        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scheduledId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void nextMatchStaysInsideTheOpenTournament() throws Exception {
        String adminToken = createAdminAndLogin("next-scope-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "next-scope-cap-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Олег","lastName":"Свой"}
                                """))
                .andExpect(status().isOk());
        String teamA = createTeam(adminToken, "Scope Home", playerId(captainToken));
        captainToken = reLogin(captainEmail, "Str0ngPass!");

        String otherEmail = "next-scope-other-" + System.nanoTime() + "@example.com";
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Илья","lastName":"Чужой"}
                                """))
                .andExpect(status().isOk());
        String teamB = createTeam(adminToken, "Scope Away", playerId(otherToken));
        otherToken = reLogin(otherEmail, "Str0ngPass!");

        String sport = sportId(adminToken);
        String openId = createTournament(adminToken, sport);
        String otherId = createTournament(adminToken, sport);
        registerAndApprove(adminToken, captainToken, openId, teamA);
        registerAndApprove(adminToken, otherToken, openId, teamB);
        registerAndApprove(adminToken, captainToken, otherId, teamA);
        registerAndApprove(adminToken, otherToken, otherId, teamB);

        Instant sooner = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = sooner.plus(3, ChronoUnit.DAYS);
        String otherMatch = createMatch(adminToken, otherId, teamA, teamB, sooner);
        String openMatch = createMatch(adminToken, openId, teamA, teamB, later);

        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherMatch))
                .andExpect(jsonPath("$.tournamentId").value(otherId));
        mockMvc.perform(get("/api/v1/matches/next").param("tournamentId", openId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(openMatch))
                .andExpect(jsonPath("$.tournamentId").value(openId));
    }

    private void retireOtherOpenMatches(String... keptMatchIds) {
        Set<UUID> kept = Arrays.stream(keptMatchIds).map(UUID::fromString).collect(Collectors.toSet());
        List<MatchStatus> open = List.of(MatchStatus.SCHEDULED, MatchStatus.LIVE, MatchStatus.PAUSED);
        for (Match match : matchRepository.findByStatusIn(open)) {
            if (kept.contains(match.getId())) {
                continue;
            }
            match.setStatus(MatchStatus.CANCELLED);
            matchRepository.save(match);
        }
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

    private String createTournament(String adminToken, String sportId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Next Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
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
                                """.formatted(name, name.substring(0, 2).toUpperCase(), captainPlayerId)))
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
