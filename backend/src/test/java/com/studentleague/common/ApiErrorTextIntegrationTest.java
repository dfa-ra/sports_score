package com.studentleague.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.auth.dto.RegistrationRoles;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorTextIntegrationTest extends AbstractIntegrationTest {

    @Test
    void plainTextLoginIsNotAServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=a@example.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Нужен запрос в формате JSON"));

        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Метод не поддерживается"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное тело запроса"));

        mockMvc.perform(get("/api/v1/sports").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.message").value("Этот формат ответа не поддерживается"));
    }

    @Test
    void playoffMissingTournamentIsRussian() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + UUID.randomUUID() + "/playoff"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Турнир не найден"))
                .andExpect(content().string(not(containsString("Tournament not found"))));
    }

    @Test
    void registrationRolePatternIsRussian() throws Exception {
        String email = "role-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!","firstName":"Иван","lastName":"Иванов","role":"COACH"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Проверьте введённые данные"))
                .andExpect(jsonPath("$.details[?(@.field=='role')].message", hasItem(RegistrationRoles.MESSAGE)))
                .andExpect(content().string(not(containsString("must match"))));
    }

    @Test
    void protocolMinuteFollowsMatchDuration() throws Exception {
        String admin = createAdminAndLogin("minute-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        Fixture shortMatch = setupMatch(admin, 1, 20);
        Fixture halves = setupMatch(admin, 2, 15);

        mockMvc.perform(post("/api/v1/admin/matches/" + shortMatch.matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goal(shortMatch, 200)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Минута больше длительности матча"));

        mockMvc.perform(post("/api/v1/admin/matches/" + halves.matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goal(halves, 16)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameTime").value(16 * 60))
                .andExpect(jsonPath("$.minute").value(16))
                .andExpect(jsonPath("$.period").value(2));
    }

    private String goal(Fixture fx, int minute) {
        return """
                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":%d}
                """.formatted(fx.homeTeamId, fx.homePlayerId, minute);
    }

    private Fixture setupMatch(String adminToken, int periodCount, int periodLengthMinutes) throws Exception {
        String stamp = System.nanoTime() + "-" + periodCount + "x" + periodLengthMinutes;
        String homeEmail = "min-home-" + stamp + "@example.com";
        String awayEmail = "min-away-" + stamp + "@example.com";
        String homeToken = registerAndLogin(homeEmail, "Str0ngPass!");
        String awayToken = registerAndLogin(awayEmail, "Str0ngPass!");

        MvcResult homePlayer = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(homeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Дома","lastName":"Игрок","jerseyNumber":9}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String homePlayerId = objectMapper.readTree(homePlayer.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(awayToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Гость","lastName":"Игрок","jerseyNumber":10}
                                """))
                .andExpect(status().isOk());

        String homeTeamId = createTeam(adminToken, "Минуты дома " + stamp, homePlayerId);
        String awayPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(awayToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
        String awayTeamId = createTeam(adminToken, "Минуты гости " + stamp, awayPlayerId);

        MvcResult sports = mockMvc.perform(get("/api/v1/sports"))
                .andExpect(status().isOk())
                .andReturn();
        String sportId = null;
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                sportId = node.get("id").asText();
                break;
            }
        }

        MvcResult tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Минуты %s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(stamp, sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tournamentId = objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();

        homeToken = loginOnly(homeEmail, "Str0ngPass!");
        awayToken = loginOnly(awayEmail, "Str0ngPass!");
        approve(adminToken, homeToken, tournamentId, homeTeamId);
        approve(adminToken, awayToken, tournamentId, awayTeamId);

        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        MvcResult match = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","periodCount":%d,"periodLengthMinutes":%d}
                                """.formatted(tournamentId, homeTeamId, awayTeamId, kickoff, periodCount, periodLengthMinutes)))
                .andExpect(status().isCreated())
                .andReturn();
        String matchId = objectMapper.readTree(match.getResponse().getContentAsString()).get("id").asText();
        return new Fixture(matchId, homeTeamId, homePlayerId);
    }

    private void approve(String adminToken, String captainToken, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\"}".formatted(teamId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    private String createTeam(String adminToken, String name, String captainPlayerId) throws Exception {
        String compact = name.replaceAll("\\s+", "");
        String shortName = compact.substring(Math.max(0, compact.length() - 12));
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, shortName, captainPlayerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private record Fixture(String matchId, String homeTeamId, String homePlayerId) {
    }
}
