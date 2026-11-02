package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentDeleteIntegrationTest extends AbstractIntegrationTest {

    private static final String PLAYED_MATCHES =
            "Нельзя удалить турнир: в нём есть сыгранные матчи";

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchEventRepository matchEventRepository;

    @Test
    void deletesEmptyTournamentAndUnplayedFixtures() throws Exception {
        String adminToken = createAdminAndLogin("tdel-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = sportId(adminToken);

        mockMvc.perform(delete("/api/v1/tournaments/" + UUID.randomUUID())
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNotFound());

        String emptyId = createTournament(adminToken, sportId, "Пустой турнир");
        String fanToken = registerAndLogin("tdel-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(delete("/api/v1/tournaments/" + emptyId)
                        .header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/tournaments/" + emptyId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tournaments/" + emptyId))
                .andExpect(status().isNotFound());

        String captainEmail = "tdel-cap-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        String awayEmail = "tdel-away-" + System.nanoTime() + "@example.com";
        String awayToken = registerAndLogin(awayEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(awayToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Bob","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        String homeId = createTeam(adminToken, "Delete Home", playerId(captainToken));
        String awayId = createTeam(adminToken, "Delete Away", playerId(awayToken));

        String tournamentId = createTournament(adminToken, sportId, "Турнир только с сеткой");
        registerAndApprove(adminToken, captainToken, tournamentId, homeId);
        awayToken = reLogin(awayEmail, "Str0ngPass!");
        registerAndApprove(adminToken, awayToken, tournamentId, awayId);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"Группа","teamIds":["%s","%s"]}]}
                                """.formatted(homeId, awayId)))
                .andExpect(status().isOk());

        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        String scheduledId = schedule(adminToken, tournamentId, homeId, awayId, kickoff);
        String cancelledId = schedule(adminToken, tournamentId, homeId, awayId, kickoff.plus(1, ChronoUnit.DAYS));
        Match cancelled = matchRepository.findById(UUID.fromString(cancelledId)).orElseThrow();
        cancelled.setStatus(MatchStatus.CANCELLED);
        matchRepository.saveAndFlush(cancelled);

        String refereeEmail = "tdel-ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        referee.setRole(Role.REFEREE);
        userRepository.save(referee);
        mockMvc.perform(post("/api/v1/matches/" + scheduledId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refereeId":"%s"}
                                """.formatted(referee.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isOk());
        Instant playoffKickoff = kickoff.plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        MvcResult opened = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pairs":[{"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"Зал"}]}
                                """.formatted(homeId, awayId, playoffKickoff)))
                .andExpect(status().isOk())
                .andReturn();
        String playoffMatchId = objectMapper.readTree(opened.getResponse().getContentAsString())
                .get("rounds").get(0).get("slots").get(0).get("matchId").asText();

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/tables"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/matches/" + scheduledId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/matches/" + cancelledId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/matches/" + playoffMatchId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teams/" + homeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Delete Home"));
    }

    @Test
    void rejectsTournamentWithPlayedMatches() throws Exception {
        String adminToken = createAdminAndLogin("tplayed-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = sportId(adminToken);
        String homeEmail = "tplayed-home-" + System.nanoTime() + "@example.com";
        String homeToken = registerAndLogin(homeEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Home","lastName":"Player"}
                                """))
                .andExpect(status().isOk());
        String awayEmail = "tplayed-away-" + System.nanoTime() + "@example.com";
        String awayToken = registerAndLogin(awayEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(awayToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Away","lastName":"Player"}
                                """))
                .andExpect(status().isOk());
        String homeId = createTeam(adminToken, "Played Home", playerId(homeToken));
        String awayId = createTeam(adminToken, "Played Away", playerId(awayToken));
        homeToken = reLogin(homeEmail, "Str0ngPass!");
        awayToken = reLogin(awayEmail, "Str0ngPass!");

        String finishedTournament = tournamentWithTeams(adminToken, homeToken, awayToken, sportId, homeId, awayId, "Сыгранный финал");
        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        String finishedId = schedule(adminToken, finishedTournament, homeId, awayId, kickoff);
        String stillScheduledId = schedule(adminToken, finishedTournament, homeId, awayId, kickoff.plus(1, ChronoUnit.HOURS));
        Match finished = matchRepository.findById(UUID.fromString(finishedId)).orElseThrow();
        finished.setStatus(MatchStatus.FINISHED);
        matchRepository.saveAndFlush(finished);
        expectConflict(adminToken, finishedTournament);
        mockMvc.perform(get("/api/v1/matches/" + finishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));
        mockMvc.perform(get("/api/v1/matches/" + stillScheduledId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        expectConflict(adminToken, tournamentWithStatus(
                adminToken, homeToken, awayToken, sportId, homeId, awayId, "Живой матч", MatchStatus.LIVE));
        expectConflict(adminToken, tournamentWithStatus(
                adminToken, homeToken, awayToken, sportId, homeId, awayId, "Пауза", MatchStatus.PAUSED));

        String scoredTournament = tournamentWithTeams(adminToken, homeToken, awayToken, sportId, homeId, awayId, "Есть счёт");
        String scoredId = schedule(adminToken, scoredTournament, homeId, awayId, kickoff);
        Match scored = matchRepository.findById(UUID.fromString(scoredId)).orElseThrow();
        scored.setHomeScore(2);
        scored.setAwayScore(1);
        matchRepository.saveAndFlush(scored);
        expectConflict(adminToken, scoredTournament);
        mockMvc.perform(get("/api/v1/matches/" + scoredId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.homeScore").value(2));

        String eventTournament = tournamentWithTeams(adminToken, homeToken, awayToken, sportId, homeId, awayId, "Есть событие");
        String eventMatchId = schedule(adminToken, eventTournament, homeId, awayId, kickoff);
        MatchEvent event = new MatchEvent();
        event.setMatchId(UUID.fromString(eventMatchId));
        event.setEventType(MatchEventType.GOAL);
        event.setTimestamp(Instant.now());
        event.setTeamId(UUID.fromString(homeId));
        matchEventRepository.saveAndFlush(event);
        expectConflict(adminToken, eventTournament);
        mockMvc.perform(get("/api/v1/matches/" + eventMatchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.homeScore").value(0));
        mockMvc.perform(get("/api/v1/tournaments/" + eventTournament))
                .andExpect(status().isOk());
    }

    private void expectConflict(String adminToken, String tournamentId) throws Exception {
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(PLAYED_MATCHES));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId))
                .andExpect(status().isOk());
    }

    private String tournamentWithStatus(
            String adminToken,
            String homeToken,
            String awayToken,
            String sportId,
            String homeId,
            String awayId,
            String name,
            MatchStatus status
    ) throws Exception {
        String tournamentId = tournamentWithTeams(adminToken, homeToken, awayToken, sportId, homeId, awayId, name);
        String matchId = schedule(adminToken, tournamentId, homeId, awayId, Instant.now().plus(3, ChronoUnit.HOURS));
        Match match = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        match.setStatus(status);
        matchRepository.saveAndFlush(match);
        return tournamentId;
    }

    private String tournamentWithTeams(
            String adminToken,
            String homeToken,
            String awayToken,
            String sportId,
            String homeId,
            String awayId,
            String name
    ) throws Exception {
        String tournamentId = createTournament(adminToken, sportId, name);
        registerAndApprove(adminToken, homeToken, tournamentId, homeId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayId);
        return tournamentId;
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

    private String schedule(String adminToken, String tournamentId, String homeId, String awayId, Instant kickoff) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeId, awayId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTournament(String adminToken, String sportId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(name, sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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

    private String sportId(String token) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String reLogin(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
