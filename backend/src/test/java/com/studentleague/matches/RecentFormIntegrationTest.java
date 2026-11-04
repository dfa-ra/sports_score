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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecentFormIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void formReturnsBothCrestsAndMatchIdInOneRow() throws Exception {
        String adminToken = createAdminAndLogin("form-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String homeName = "Север " + System.nanoTime();
        String awayName = "Юг " + System.nanoTime();
        String homeId = createTeam(adminToken, homeName, "СЕВ", "/media/north.png");
        String awayId = createTeam(adminToken, awayName, "ЮГ", "/media/south.png");
        String sportId = sportId(adminToken);
        String tournamentId = createTournament(adminToken, sportId);

        Instant older = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant newer = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID latestId = saveMatch(tournamentId, sportId, homeId, awayId, newer, MatchStatus.FINISHED, 2, 1);
        saveMatch(tournamentId, sportId, awayId, homeId, older, MatchStatus.FINISHED, 0, 4);
        saveMatch(tournamentId, sportId, homeId, awayId, Instant.now().plus(2, ChronoUnit.DAYS), MatchStatus.SCHEDULED, 0, 0);

        mockMvc.perform(get("/api/v1/teams/" + homeId + "/form").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(latestId.toString()))
                .andExpect(jsonPath("$[0].homeTeamId").value(homeId))
                .andExpect(jsonPath("$[0].awayTeamId").value(awayId))
                .andExpect(jsonPath("$[0].homeTeamName").value(homeName))
                .andExpect(jsonPath("$[0].homeTeamShortName").value("СЕВ"))
                .andExpect(jsonPath("$[0].homeLogoUrl").value("/media/north.png"))
                .andExpect(jsonPath("$[0].awayTeamName").value(awayName))
                .andExpect(jsonPath("$[0].awayLogoUrl").value("/media/south.png"))
                .andExpect(jsonPath("$[0].homeScore").value(2))
                .andExpect(jsonPath("$[0].awayScore").value(1))
                .andExpect(jsonPath("$[0].status").value("FINISHED"))
                .andExpect(jsonPath("$[1].homeTeamId").value(awayId))
                .andExpect(jsonPath("$[1].awayTeamId").value(homeId))
                .andExpect(jsonPath("$[1].homeLogoUrl").value("/media/south.png"))
                .andExpect(jsonPath("$[1].awayLogoUrl").value("/media/north.png"))
                .andExpect(jsonPath("$[1].homeScore").value(0))
                .andExpect(jsonPath("$[1].awayScore").value(4));
    }

    private UUID saveMatch(
            String tournamentId,
            String sportId,
            String homeId,
            String awayId,
            Instant scheduledAt,
            MatchStatus status,
            int homeScore,
            int awayScore
    ) {
        Match match = new Match();
        match.setTournamentId(UUID.fromString(tournamentId));
        match.setSportId(UUID.fromString(sportId));
        match.setHomeTeamId(UUID.fromString(homeId));
        match.setAwayTeamId(UUID.fromString(awayId));
        match.setScheduledAt(scheduledAt);
        match.setStatus(status);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return matchRepository.save(match).getId();
    }

    private String createTeam(String adminToken, String name, String shortName, String logoUrl) throws Exception {
        String email = "form-" + System.nanoTime() + "@example.com";
        String token = registerAndLogin(email, "Str0ngPass!");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Cap","lastName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isOk());
        MvcResult me = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        String playerId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();
        MvcResult team = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","logoUrl":"%s","captainPlayerId":"%s"}
                                """.formatted(name, shortName, logoUrl, playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(team.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTournament(String adminToken, String sportId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Form Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
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
}
