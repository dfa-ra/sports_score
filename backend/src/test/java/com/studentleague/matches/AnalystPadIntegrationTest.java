package com.studentleague.matches;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.domain.PossessionSide;
import com.studentleague.matches.entity.MatchAnalystStats;
import com.studentleague.matches.repository.MatchAnalystStatsRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalystPadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchAnalystStatsRepository analystStatsRepository;

    @Test
    void analystRoleIsOptInAndPadStatsStayOffTheRefereeFoulCount() throws Exception {
        String adminToken = createAdminAndLogin("analyst-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fanEmail = "analyst-fan-" + System.nanoTime() + "@example.com";
        String fanToken = registerAndLogin(fanEmail, "Str0ngPass!");
        String fanId = userRepository.findByEmailIgnoreCase(fanEmail).orElseThrow().getId().toString();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", auth(fanToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role=='ANALYST')]").doesNotExist());

        mockMvc.perform(patch("/api/v1/admin/users/" + fanId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"FAN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role=='ANALYST' && @.status=='APPROVED')]").doesNotExist());

        Fixture fx = setupMatch(adminToken);

        String playerEmail = "analyst-player-" + System.nanoTime() + "@example.com";
        String playerToken = registerAndLogin(playerEmail, "Str0ngPass!");
        String playerId = userRepository.findByEmailIgnoreCase(playerEmail).orElseThrow().getId().toString();
        mockMvc.perform(patch("/api/v1/admin/users/" + playerId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"FAN\",\"PLAYER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role=='ANALYST')]").doesNotExist());

        postStat(fanToken, fx.matchId, fx.homeTeamId, "SHOT_ON_TARGET").andExpect(status().isForbidden());
        postStat(playerToken, fx.matchId, fx.homeTeamId, "SHOT_ON_TARGET").andExpect(status().isForbidden());
        postStat(fx.captainToken, fx.matchId, fx.homeTeamId, "SHOT_ON_TARGET").andExpect(status().isForbidden());
        postStat(fx.refereeToken, fx.matchId, fx.homeTeamId, "FOUL").andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/users/" + fanId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"FAN\",\"ANALYST\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role=='ANALYST' && @.status=='APPROVED')]").exists());

        postStat(fanToken, fx.matchId, fx.homeTeamId, "SHOT_ON_TARGET")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home.shots").value(1))
                .andExpect(jsonPath("$.home.shotsOnTarget").value(1));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/analyst-stats"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.home.shots").value(1))
                .andExpect(jsonPath("$.home.shotsOnTarget").value(1))
                .andExpect(jsonPath("$.possessionTracked").value(false));

        postStat(adminToken, fx.matchId, fx.awayTeamId, "SAVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.away.saves").value(1));

        postStat(fanToken, fx.matchId, fx.homeTeamId, "FOUL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home.fouls").value(1))
                .andExpect(jsonPath("$.home.shots").value(1))
                .andExpect(jsonPath("$.home.shotsOnTarget").value(1));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/futsal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].fouls").value(0))
                .andExpect(jsonPath("$.teams[0].tenMeters").value(false))
                .andExpect(jsonPath("$.teams[1].fouls").value(0))
                .andExpect(jsonPath("$.teams[1].tenMeters").value(false));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType=='FOUL')]").doesNotExist());

        mockMvc.perform(post("/api/v1/analyst/matches/" + fx.matchId + "/possession")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"side\":\"HOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possessionTracked").value(true))
                .andExpect(jsonPath("$.possessionSide").value("HOME"));

        mockMvc.perform(post("/api/v1/analyst/matches/" + fx.matchId + "/possession")
                        .header("Authorization", auth(fanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"side\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possessionSide").value("PAUSED"))
                .andExpect(jsonPath("$.possessionTracked").value(true));

        MatchAnalystStats stored = analystStatsRepository.findById(UUID.fromString(fx.matchId)).orElseThrow();
        stored.getHome().setPossessionSeconds(40);
        stored.getAway().setPossessionSeconds(10);
        stored.setPossessionSide(PossessionSide.PAUSED);
        stored.setPossessionSince(null);
        analystStatsRepository.saveAndFlush(stored);

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/analyst-stats"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.possessionTracked").value(true))
                .andExpect(jsonPath("$.possessionSide").value("PAUSED"))
                .andExpect(jsonPath("$.homePossessionSeconds").value(40))
                .andExpect(jsonPath("$.awayPossessionSeconds").value(10))
                .andExpect(jsonPath("$.homePossessionPercent").value(80))
                .andExpect(jsonPath("$.awayPossessionPercent").value(20))
                .andExpect(jsonPath("$.home.fouls").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions postStat(
            String token,
            String matchId,
            String teamId,
            String stat
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/analyst/matches/" + matchId + "/stats")
                .header("Authorization", auth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"teamId":"%s","stat":"%s"}
                        """.formatted(teamId, stat)));
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String homeEmail = "an-home-" + System.nanoTime() + "@example.com";
        String awayEmail = "an-away-" + System.nanoTime() + "@example.com";
        String homeToken = registerAndLogin(homeEmail, "Str0ngPass!");
        String awayToken = registerAndLogin(awayEmail, "Str0ngPass!");

        MvcResult homePlayer = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Home","lastName":"Player","jerseyNumber":9}
                                """))
                .andExpect(status().isOk()).andReturn();
        String homePlayerId = objectMapper.readTree(homePlayer.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(awayToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Away","lastName":"Player","jerseyNumber":10}
                                """))
                .andExpect(status().isOk());
        String awayPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(awayToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        String homeTeamId = createTeam(adminToken, "Analyst Home " + System.nanoTime(), homePlayerId);
        String awayTeamId = createTeam(adminToken, "Analyst Away " + System.nanoTime(), awayPlayerId);

        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk()).andReturn();
        String sportId = null;
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FUTSAL".equals(node.get("code").asText())) {
                sportId = node.get("id").asText();
                break;
            }
        }
        if (sportId == null) {
            throw new IllegalStateException("FUTSAL sport is not seeded");
        }

        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Analyst Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated()).andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = loginOnly(homeEmail, "Str0ngPass!");
        awayToken = loginOnly(awayEmail, "Str0ngPass!");
        registerAndApprove(adminToken, homeToken, tournamentId, homeTeamId);
        registerAndApprove(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff)))
                .andExpect(status().isCreated()).andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();

        String refereeEmail = "an-ref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        roleService.grantApproved(referee, Role.REFEREE, "https://example.com/ref.jpg");
        String refereeToken = loginOnly(refereeEmail, "Str0ngPass!");

        return new Fixture(matchId, homeTeamId, awayTeamId, homeToken, awayToken, refereeToken);
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

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        String shortName = name.replaceAll("[^A-Za-z]", "");
        if (shortName.length() < 2) {
            shortName = "TM";
        }
        shortName = shortName.substring(0, Math.min(4, shortName.length())).toUpperCase();
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, shortName, captainPlayerId)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private record Fixture(
            String matchId,
            String homeTeamId,
            String awayTeamId,
            String captainToken,
            String playerToken,
            String refereeToken
    ) {
    }
}
