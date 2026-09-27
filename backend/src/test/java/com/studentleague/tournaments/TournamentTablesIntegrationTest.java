package com.studentleague.tournaments;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentTablesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void adminAssignsGroupsAndStandingsStayIntraGroup() throws Exception {
        String adminToken = createAdminAndLogin("tables-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String teamA = registerApprovedTeam(adminToken, "Alpha Group");
        String teamB = registerApprovedTeam(adminToken, "Beta Group");
        String teamC = registerApprovedTeam(adminToken, "Gamma Group");

        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String sportId = objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();

        MvcResult tournamentResult = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ITMO Groups","sportId":"%s","seasonYear":2026,"format":"GROUPS_PLAYOFF","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournamentResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamA)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamB)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamC)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamA + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamB + "/approve")
                .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamC + "/approve")
                .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());

        finishMatch(tournamentId, UUID.fromString(sportId), UUID.fromString(teamA), UUID.fromString(teamB), 3, 0);
        finishMatch(tournamentId, UUID.fromString(sportId), UUID.fromString(teamA), UUID.fromString(teamC), 1, 0);

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[
                                  {"name":"Группа A","teamIds":["%s","%s"]},
                                  {"name":"Группа B","teamIds":["%s"]}
                                ]}
                                """.formatted(teamA, teamB, teamC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Группа A"))
                .andExpect(jsonPath("$[0].teamIds.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Группа B"));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[
                                  {"name":"A","teamIds":["%s"]},
                                  {"name":"B","teamIds":["%s"]}
                                ]}
                                """.formatted(teamA, teamA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(2))
                .andExpect(jsonPath("$.tables[0].name").value("Группа A"))
                .andExpect(jsonPath("$.tables[0].rows.length()").value(2))
                .andExpect(jsonPath("$.tables[0].rows[0].teamId").value(teamA))
                .andExpect(jsonPath("$.tables[0].rows[0].points").value(3))
                .andExpect(jsonPath("$.tables[0].rows[1].points").value(0))
                .andExpect(jsonPath("$.tables[1].name").value("Группа B"))
                .andExpect(jsonPath("$.tables[1].rows.length()").value(1))
                .andExpect(jsonPath("$.tables[1].rows[0].played").value(0));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tables\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.tables[0].rows.length()").value(3));
    }

    private String registerApprovedTeam(String adminToken, String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '-') + "-" + System.nanoTime() + "@example.com";
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
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, 2).toUpperCase(), playerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(team.getResponse().getContentAsString()).get("id").asText();
    }

    private void finishMatch(String tournamentId, UUID sportId, UUID home, UUID away, int homeScore, int awayScore) {
        Match match = new Match();
        match.setTournamentId(UUID.fromString(tournamentId));
        match.setSportId(sportId);
        match.setHomeTeamId(home);
        match.setAwayTeamId(away);
        match.setScheduledAt(Instant.now());
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        matchRepository.save(match);
    }
}
