package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.PlayoffSlotKind;
import com.studentleague.tournaments.entity.PlayoffSlot;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private PlayoffSlotRepository playoffSlotRepository;

    @Test
    void catalogBatchesCountsAndLeavesThePagedListAlone() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        String admin = createAdminAndLogin("catalog-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String alphaEmail = "catalog-a-" + System.nanoTime() + "@example.com";
        String betaEmail = "catalog-b-" + System.nanoTime() + "@example.com";
        String alphaCaptain = registerAndLogin(alphaEmail, "Str0ngPass!");
        String betaCaptain = registerAndLogin(betaEmail, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(alphaCaptain))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Kate","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(betaCaptain))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Boris","lastName":"Captain"}
                                """))
                .andExpect(status().isOk());
        String alphaName = "Alpha " + System.nanoTime();
        String betaName = "Beta " + System.nanoTime();
        String alpha = createTeam(admin, alphaName, playerId(alphaCaptain));
        String beta = createTeam(admin, betaName, playerId(betaCaptain));
        alphaCaptain = reLogin(alphaEmail, "Str0ngPass!");
        betaCaptain = reLogin(betaEmail, "Str0ngPass!");
        String sportId = sportId(admin);

        String tableId = createTournament(admin, sportId, "Round robin catalog", "ROUND_ROBIN", "REGISTRATION");
        approve(admin, alphaCaptain, tableId, alpha);
        approve(admin, betaCaptain, tableId, beta);
        String played = schedule(admin, tableId, alpha, beta);
        var finished = matchRepository.findById(UUID.fromString(played)).orElseThrow();
        finished.setStatus(MatchStatus.FINISHED);
        finished.setHomeScore(2);
        finished.setAwayScore(1);
        matchRepository.saveAndFlush(finished);
        mockMvc.perform(put("/api/v1/tournaments/" + tableId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FINISHED"}
                                """))
                .andExpect(status().isOk());

        String cupId = createTournament(admin, sportId, "Cup catalog", "CUP", "REGISTRATION");
        approve(admin, alphaCaptain, cupId, alpha);
        approve(admin, betaCaptain, cupId, beta);
        Tournament cup = tournamentRepository.findById(UUID.fromString(cupId)).orElseThrow();
        cup.setStatus(com.studentleague.tournaments.domain.TournamentStatus.ACTIVE);
        tournamentRepository.saveAndFlush(cup);
        String liveId = schedule(admin, cupId, alpha, beta);
        String waitingId = schedule(admin, cupId, beta, alpha);
        cup.setCompetitionStage(CompetitionStage.PLAYOFF);
        tournamentRepository.saveAndFlush(cup);
        var live = matchRepository.findById(UUID.fromString(liveId)).orElseThrow();
        live.setStatus(MatchStatus.LIVE);
        live.setBracketRound(1);
        live.setHomeScore(1);
        live.setAwayScore(0);
        matchRepository.saveAndFlush(live);
        var waiting = matchRepository.findById(UUID.fromString(waitingId)).orElseThrow();
        waiting.setBracketRound(1);
        matchRepository.saveAndFlush(waiting);
        PlayoffSlot slot = new PlayoffSlot();
        slot.setTournamentId(cup.getId());
        slot.setRoundNumber(1);
        slot.setSlotIndex(0);
        slot.setKind(PlayoffSlotKind.MAIN);
        slot.setMatchId(live.getId());
        slot.setHomeTeamId(UUID.fromString(alpha));
        slot.setAwayTeamId(UUID.fromString(beta));
        playoffSlotRepository.saveAndFlush(slot);

        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get("/api/v1/tournaments/catalog"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        JsonNode table = row(catalog, tableId);
        assertThat(table.get("teamsCount").asInt()).isEqualTo(2);
        assertThat(table.get("winnerName").asText()).isEqualTo(alphaName);
        assertThat(table.get("winnerTeamId").asText()).isEqualTo(alpha);
        assertThat(table.get("liveCount").asInt()).isEqualTo(0);
        assertThat(table.get("progress").isNull()).isTrue();
        assertThat(table.get("status").asText()).isEqualTo("FINISHED");

        JsonNode bracket = row(catalog, cupId);
        assertThat(bracket.get("teamsCount").asInt()).isEqualTo(2);
        assertThat(bracket.get("liveCount").asInt()).isEqualTo(1);
        assertThat(bracket.get("progress").asDouble()).isEqualTo(0.0);
        assertThat(bracket.get("roundTitle").asText()).isEqualTo("Финал");
        assertThat(bracket.get("stage").asText()).isEqualTo("PLAYOFF");
        assertThat(bracket.get("winnerName").isNull()).isTrue();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/tournaments").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        JsonNode listed = null;
        for (JsonNode item : list.get("content")) {
            if (tableId.equals(item.get("id").asText())) {
                listed = item;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("name").asText()).isEqualTo("Round robin catalog");
        assertThat(listed.has("teamsCount")).isFalse();
        assertThat(listed.has("liveCount")).isFalse();
        assertThat(listed.has("winnerName")).isFalse();
    }

    private static JsonNode row(JsonNode catalog, String id) {
        for (JsonNode item : catalog) {
            if (id.equals(item.get("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("catalog row missing " + id);
    }

    private String sportId(String admin) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get(0).get("id").asText();
    }

    private String createTournament(String admin, String sportId, String name, String format, String status) throws Exception {
        String body = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2026,"format":"%s","status":"%s"}
                                """.formatted(name, sportId, format, status)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void approve(String admin, String captain, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captain))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    private String schedule(String admin, String tournamentId, String home, String away) throws Exception {
        String body = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, home, away, Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createTeam(String admin, String name, String captainPlayerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"КК","captainPlayerId":"%s"}
                                """.formatted(name, captainPlayerId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String playerId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String reLogin(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
