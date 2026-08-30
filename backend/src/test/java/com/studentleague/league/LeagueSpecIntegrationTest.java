package com.studentleague.league;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeagueSpecIntegrationTest extends AbstractIntegrationTest {

    @Test
    void playerRegistrationRequiresPhotoAndFanCannotOpenMyTeam() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nophoto-%s@example.com","password":"Str0ngPass!","firstName":"Нет","lastName":"Фото","role":"PLAYER"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isBadRequest());

        String fanToken = registerAndLogin("fan-mine-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(get("/api/v1/teams/mine").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminImportsCsvCalendar() throws Exception {
        String adminToken = createAdminAndLogin("cal-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String homeToken = registerAndLogin("cal-h-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/h.jpg");
        String awayToken = registerAndLogin("cal-a-" + System.nanoTime() + "@example.com", "Str0ngPass!", "PLAYER", "https://example.com/a.jpg");

        String homePlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(homeToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
        String awayPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(awayToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Календарь Хозяева","shortName":"КХ","captainPlayerId":"%s"}
                                """.formatted(homePlayerId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Календарь Гости","shortName":"КГ","captainPlayerId":"%s"}
                                """.formatted(awayPlayerId)))
                .andExpect(status().isCreated());

        String sportId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/sports")).andReturn().getResponse().getContentAsString()
        ).get(0).get("id").asText();

        String tournamentId = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/tournaments")
                                .header("Authorization", auth(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Осенний кубок","sportId":"%s","seasonYear":2026,"format":"CUP","status":"ACTIVE","regulations":"До 18 в заявке"}
                                        """.formatted(sportId)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.format").value("CUP"))
                        .andExpect(jsonPath("$.regulations").value("До 18 в заявке"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();

        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "calendar.csv",
                "text/csv",
                "date,time,home,away\n2026-09-12,18:00,Календарь Хозяева,Календарь Гости\n".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/tournaments/" + tournamentId + "/calendar/import")
                        .file(csv)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournament.name").value("Осенний кубок"));
    }
}
