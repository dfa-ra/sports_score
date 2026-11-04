package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiRegression108IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void calendarImportUpdatesTheSameFixtureAndRejectsAPlayedChange() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("imp108-" + suffix + "@example.com", "Str0ngPass!");
        String north = "Север " + suffix;
        String west = "Запад " + suffix;
        captainAndTeam(admin, "n108-" + suffix + "@example.com", north);
        captainAndTeam(admin, "w108-" + suffix + "@example.com", west);
        String tournamentId = tournament(admin, "Календарь " + suffix, "REGISTRATION");

        String csv = """
                date,time,home,away,venue,round
                2026-09-12,18:00,%s,%s,Зал А,1
                2026-09-13,19:00,%s,%s,Зал Б,1
                """.formatted(north, west, west, north);
        importFile(admin, tournamentId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0));

        importFile(admin, tournamentId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(2));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.venue=='Зал А')]").isNotEmpty());

        String moved = """
                date,time,home,away,venue,round
                2026-09-20,18:00,%s,%s,Зал В,1
                2026-09-21,19:00,%s,%s,Зал Г,1
                """.formatted(north, west, west, north);
        importFile(admin, tournamentId, moved)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(2));

        JsonNode matches = matches(tournamentId);
        String playedId = null;
        String otherId = null;
        String otherKickoff = null;
        for (JsonNode match : matches) {
            if (north.equals(match.get("homeTeamName").asText())) {
                playedId = match.get("id").asText();
            } else {
                otherId = match.get("id").asText();
                otherKickoff = match.get("scheduledAt").asText();
            }
        }
        Match played = matchRepository.findById(UUID.fromString(playedId)).orElseThrow();
        played.setStatus(MatchStatus.FINISHED);
        matchRepository.saveAndFlush(played);

        String conflict = """
                date,time,home,away,venue,round
                2026-09-20,18:00,%s,%s,Зал В,1
                2026-10-01,21:00,%s,%s,Другой зал,1
                """.formatted(west, north, north, west);
        importFile(admin, tournamentId, conflict)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Строка 3: матч уже сыгран"));

        JsonNode after = matches(tournamentId);
        assertEquals(2, after.size());
        for (JsonNode match : after) {
            if (otherId.equals(match.get("id").asText())) {
                assertEquals(otherKickoff, match.get("scheduledAt").asText());
                assertEquals("Зал Г", match.get("venue").asText());
            }
            if (playedId.equals(match.get("id").asText())) {
                assertEquals("Зал В", match.get("venue").asText());
            }
        }
    }

    @Test
    void samePairInAnotherRoundIsASecondMatch() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rnd108-" + suffix + "@example.com", "Str0ngPass!");
        String north = "Север " + suffix;
        String west = "Запад " + suffix;
        captainAndTeam(admin, "nr108-" + suffix + "@example.com", north);
        captainAndTeam(admin, "wr108-" + suffix + "@example.com", west);
        String tournamentId = tournament(admin, "Туры " + suffix, "REGISTRATION");
        String csv = """
                date,time,home,away,round
                2026-09-12,18:00,%s,%s,1
                2026-10-12,18:00,%s,%s,2
                """.formatted(north, west, north, west);
        importFile(admin, tournamentId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));
        importFile(admin, tournamentId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(2));
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/matches"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void calendarImportWithoutAFileIsBadRequest() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("nofile-" + suffix + "@example.com", "Str0ngPass!");
        String tournamentId = tournament(admin, "Пустой файл " + suffix, "REGISTRATION");
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/calendar/import")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Файл не приложен"));
    }

    @Test
    void duplicateTeamNameIsConflictAndEmptyNameIsBadRequest() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("name108-" + suffix + "@example.com", "Str0ngPass!");
        String captain = registerAndLogin("cap108-" + suffix + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");
        String other = registerAndLogin("cap2-108-" + suffix + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/d.jpg");
        String fan = registerAndLogin("fan108-" + suffix + "@example.com", "Str0ngPass!");
        String captainId = playerId(captain);
        String otherId = playerId(other);
        String teamId = createTeam(admin, "Дубль " + suffix, captainId);
        String secondId = createTeam(admin, "Вторая " + suffix, otherId);

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  дубль %s  ","shortName":"Д"}
                                """.formatted(suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Команда с таким названием уже есть"));

        mockMvc.perform(put("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Дубль %s  "}
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Дубль " + suffix));

        mockMvc.perform(put("/api/v1/teams/" + secondId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"дубль %s"}
                                """.formatted(suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Команда с таким названием уже есть"));

        mockMvc.perform(put("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Название не может быть пустым"));

        mockMvc.perform(put("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unauthorizedInvalidBodyIsForbiddenBeforeValidation() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("authz-" + suffix + "@example.com", "Str0ngPass!");
        String fan = registerAndLogin("authz-fan-" + suffix + "@example.com", "Str0ngPass!");
        String analystEmail = "authz-an-" + suffix + "@example.com";
        registerAndLogin(analystEmail, "Str0ngPass!");
        User analystUser = userRepository.findByEmailIgnoreCase(analystEmail).orElseThrow();
        roleService.grantApproved(analystUser, Role.ANALYST, null);
        String analyst = loginOnly(analystEmail, "Str0ngPass!");
        String tournamentId = tournament(admin, "Права " + suffix, "ACTIVE");
        String captain = registerAndLogin("authz-cap-" + suffix + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/a.jpg");
        String teamId = createTeam(admin, "Права " + suffix, playerId(captain));

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(captain))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запись на турнир закрыта"));

        String stranger = registerAndLogin("authz-str-" + suffix + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/s.jpg");
        createTeam(admin, "Чужая " + suffix, playerId(stranger));
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Команду заявляет только её капитан"));

        mockMvc.perform(post("/api/v1/analyst/matches/" + UUID.randomUUID() + "/stats")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/analyst/matches/" + UUID.randomUUID() + "/stats")
                        .header("Authorization", auth(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referee/matches/" + UUID.randomUUID() + "/events")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/matches/" + UUID.randomUUID() + "/lineups")
                        .header("Authorization", auth(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anySignedInRoleWithoutATeamGetsNull() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        mockMvc.perform(get("/api/v1/teams/mine"))
                .andExpect(status().isUnauthorized());

        String fan = registerAndLogin("mine-fan-" + suffix + "@example.com", "Str0ngPass!");
        String admin = createAdminAndLogin("mine-admin-" + suffix + "@example.com", "Str0ngPass!");
        String referee = registerAndLogin("mine-ref-" + suffix + "@example.com", "Str0ngPass!", "REFEREE", "https://example.com/r.jpg");
        String analystEmail = "mine-an-" + suffix + "@example.com";
        registerAndLogin(analystEmail, "Str0ngPass!");
        User analystUser = userRepository.findByEmailIgnoreCase(analystEmail).orElseThrow();
        roleService.grantApproved(analystUser, Role.ANALYST, null);
        String analyst = loginOnly(analystEmail, "Str0ngPass!");
        String captain = registerAndLogin("mine-cap-" + suffix + "@example.com", "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");
        String teamId = createTeam(admin, "Моя " + suffix, playerId(captain));

        for (String token : new String[] {fan, admin, referee, analyst}) {
            mockMvc.perform(get("/api/v1/teams/mine").header("Authorization", auth(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("null"));
        }
        mockMvc.perform(get("/api/v1/teams/mine").header("Authorization", auth(captain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teamId));
    }

    @Test
    void forgotPasswordAndPhotoTicketAreLimitedPerRealClient() throws Exception {
        String email = "forgot-" + System.nanoTime() + "@example.com";
        for (int i = 0; i < 5; i++) {
            forgot(email, "203.0.113.40, 172.18.0.4", null).andExpect(status().isNoContent());
        }
        forgot(email, "198.51.100.9, 203.0.113.40, 172.18.0.4", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")));
        forgot(email, "203.0.113.41, 172.18.0.4", null).andExpect(status().isNoContent());
        forgot("other-" + email, "203.0.113.40, 172.18.0.4", null).andExpect(status().isNoContent());

        for (int i = 0; i < 10; i++) {
            photo("198.51.100.70", null).andExpect(status().isOk());
        }
        photo("198.51.100.70", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        photo("198.51.100.71", null).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            forgot("real-" + email, "172.18.0.4", "198.51.100.77").andExpect(status().isNoContent());
        }
        forgot("real-" + email, "172.18.0.4", "198.51.100.77")
                .andExpect(status().isTooManyRequests());
        forgot("real-" + email, "172.18.0.4", "198.51.100.78").andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email, String forwardedFor, String realIp) throws Exception {
        var request = post("/api/v1/auth/forgot-password")
                .with(req -> {
                    req.setRemoteAddr("172.18.0.5");
                    return req;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email));
        if (realIp != null) {
            request.header("X-Real-IP", realIp);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions photo(String client, String realIp) throws Exception {
        var request = post("/api/v1/auth/photo-ticket")
                .with(req -> {
                    req.setRemoteAddr("172.18.0.5");
                    return req;
                })
                .header("X-Forwarded-For", client + ", 172.18.0.4");
        if (realIp != null) {
            request.header("X-Real-IP", realIp);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions importFile(String admin, String tournamentId, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "calendar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/tournaments/" + tournamentId + "/calendar/import")
                .file(file)
                .header("Authorization", auth(admin)));
    }

    private JsonNode matches(String tournamentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/matches?size=20"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("content");
    }

    private void captainAndTeam(String admin, String email, String teamName) throws Exception {
        String captain = registerAndLogin(email, "Str0ngPass!", "CAPTAIN", "https://example.com/p.jpg");
        createTeam(admin, teamName, playerId(captain));
    }

    private String playerId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTeam(String admin, String name, String captainId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"К","captainPlayerId":"%s"}
                                """.formatted(name, captainId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String tournament(String admin, String name, String status) throws Exception {
        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports")).andReturn().getResponse().getContentAsString()
        ).get(0).get("id").asText();
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"%s"}
                                """.formatted(name, sportId, status)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
