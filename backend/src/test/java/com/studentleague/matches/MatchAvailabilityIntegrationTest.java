package com.studentleague.matches;

import com.studentleague.matches.domain.AvailabilityStatus;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.repository.MatchAvailabilityRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchAvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchAvailabilityRepository availabilityRepository;

    @Test
    void playerUpdatesOwnRowTeammatesCanReadStrangerCannot() throws Exception {
        String adminToken = createAdminAndLogin("rsvp-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainToken = registerAndLogin(
                "rsvp-cap-" + System.nanoTime() + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");
        String playerToken = registerAndLogin(
                "rsvp-pl-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String teammateToken = registerAndLogin(
                "rsvp-mate-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/m.jpg");
        String awayToken = registerAndLogin(
                "rsvp-away-" + System.nanoTime() + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/a.jpg");
        String strangerToken = registerAndLogin(
                "rsvp-str-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/s.jpg");

        String captainId = playerId(captainToken);
        String playerId = playerId(playerToken);
        String teammateId = playerId(teammateToken);
        String awayCaptainId = playerId(awayToken);

        String homeTeamId = createTeam(adminToken, "Явка Хоум " + System.nanoTime(), captainId);
        String awayTeamId = createTeam(adminToken, "Явка Гости " + System.nanoTime(), awayCaptainId);
        addMember(adminToken, homeTeamId, playerId);
        addMember(adminToken, homeTeamId, teammateId);

        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get(0).get("id").asText();

        String tournamentId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/tournaments")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Явка %s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                        """.formatted(System.nanoTime(), sportId)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();
        registerAndApprove(adminToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        String matchId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/matches")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                        """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("SCHEDULED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();

        MvcResult going = postStatus(playerToken, matchId, """
                {"status":"GOING"}
                """);
        String rowId = objectMapper.readTree(going.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"NOT_GOING","playerId":"%s"}
                                """.formatted(teammateId)))
                .andExpect(status().isForbidden());

        MvcResult notGoing = postStatus(playerToken, matchId, """
                {"status":"NOT_GOING"}
                """);
        assertThat(objectMapper.readTree(notGoing.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(rowId);
        assertThat(objectMapper.readTree(notGoing.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("NOT_GOING");

        var rows = availabilityRepository.findByMatchId(UUID.fromString(matchId));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(UUID.fromString(rowId));
        assertThat(rows.get(0).getPlayerId()).isEqualTo(UUID.fromString(playerId));
        assertThat(rows.get(0).getStatus()).isEqualTo(AvailabilityStatus.NOT_GOING);
        assertThat(rows.get(0).getUpdatedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(captainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(0))
                .andExpect(jsonPath("$.notGoingCount").value(1))
                .andExpect(jsonPath("$.notGoing[0].playerId").value(playerId))
                .andExpect(jsonPath("$.notGoing[0].status").value("NOT_GOING"))
                .andExpect(jsonPath("$.notGoing[0].id").value(rowId));

        mockMvc.perform(get("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(teammateToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notGoingCount").value(1))
                .andExpect(jsonPath("$.notGoing[0].playerId").value(playerId));

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"GOING"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(strangerToken)))
                .andExpect(status().isForbidden());

        var finished = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        finished.setStatus(MatchStatus.FINISHED);
        matchRepository.saveAndFlush(finished);

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"GOING"}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(availabilityRepository.findByMatchId(UUID.fromString(matchId)))
                .singleElement()
                .extracting(row -> row.getStatus())
                .isEqualTo(AvailabilityStatus.NOT_GOING);
    }

    private MvcResult postStatus(String token, String matchId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/matches/" + matchId + "/availability")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andReturn();
    }

    private String playerId(String token) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(post("/api/v1/teams")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s","shortName":"ЯВ","captainPlayerId":"%s"}
                                        """.formatted(name, captainPlayerId)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();
    }

    private void addMember(String adminToken, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated());
    }

    private void registerAndApprove(String adminToken, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }
}
