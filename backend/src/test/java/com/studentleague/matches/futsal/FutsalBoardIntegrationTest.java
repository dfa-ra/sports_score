package com.studentleague.matches.futsal;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FutsalBoardIntegrationTest extends AbstractIntegrationTest {

    @Test
    void foulCountGrowsSixthIsFlaggedAndSecondTimeoutInTheHalfIsRejected() throws Exception {
        String adminToken = createAdminAndLogin("fadmin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture fx = setupMatch(adminToken);

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/start")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(1));

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                            .header("Authorization", auth(fx.refereeToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"eventType":"FOUL","teamId":"%s"}
                                    """.formatted(fx.homeTeamId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.metadata.tenMeters").doesNotExist());

            mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/futsal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.foulScope").value("PERIOD"))
                    .andExpect(jsonPath("$.teams[0].teamId").value(fx.homeTeamId))
                    .andExpect(jsonPath("$.teams[0].fouls").value(i))
                    .andExpect(jsonPath("$.teams[0].tenMeters").value(false))
                    .andExpect(jsonPath("$.teams[1].fouls").value(0));
        }

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"FOUL","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("FOUL"))
                .andExpect(jsonPath("$.metadata.tenMeters").value(true));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/futsal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].fouls").value(6))
                .andExpect(jsonPath("$.teams[0].tenMeters").value(true))
                .andExpect(jsonPath("$.scopeNote").value(FutsalBoardCalculator.PERIOD_NOTE));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"TIMEOUT","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("TIMEOUT"));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"TIMEOUT","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/futsal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].timeoutAvailable").value(false))
                .andExpect(jsonPath("$.teams[1].timeoutAvailable").value(true));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/next-period")
                        .header("Authorization", auth(fx.refereeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(2));

        mockMvc.perform(get("/api/v1/matches/" + fx.matchId + "/futsal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(2))
                .andExpect(jsonPath("$.teams[0].fouls").value(0))
                .andExpect(jsonPath("$.teams[0].tenMeters").value(false))
                .andExpect(jsonPath("$.teams[0].timeoutAvailable").value(true));

        mockMvc.perform(post("/api/v1/referee/matches/" + fx.matchId + "/events")
                        .header("Authorization", auth(fx.refereeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"TIMEOUT","teamId":"%s"}
                                """.formatted(fx.homeTeamId)))
                .andExpect(status().isCreated());
    }

    private Fixture setupMatch(String adminToken) throws Exception {
        String homeCapEmail = "fhcap-" + System.nanoTime() + "@example.com";
        String awayCapEmail = "facap-" + System.nanoTime() + "@example.com";
        String homeToken = registerAndLogin(homeCapEmail, "Str0ngPass!");
        String awayToken = registerAndLogin(awayCapEmail, "Str0ngPass!");

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

        String homeTeamId = createTeam(adminToken, "Futsal Home " + System.nanoTime(), homePlayerId);
        String awayPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(awayToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
        String awayTeamId = createTeam(adminToken, "Futsal Away " + System.nanoTime(), awayPlayerId);

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
                                {"name":"Futsal Cup","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated()).andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = reLogin(homeCapEmail, "Str0ngPass!");
        awayToken = reLogin(awayCapEmail, "Str0ngPass!");
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

        String refereeEmail = "fref-" + System.nanoTime() + "@example.com";
        registerAndLogin(refereeEmail, "Str0ngPass!");
        var referee = userRepository.findByEmailIgnoreCase(refereeEmail).orElseThrow();
        referee.setRole(Role.REFEREE);
        userRepository.save(referee);
        String refereeToken = reLogin(refereeEmail, "Str0ngPass!");

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/referees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refereeId":"%s"}
                                """.formatted(referee.getId())))
                .andExpect(status().isCreated());

        return new Fixture(matchId, homeTeamId, refereeToken);
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

    private String reLogin(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private record Fixture(String matchId, String homeTeamId, String refereeToken) {
    }
}
