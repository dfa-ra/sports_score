package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WithdrawnTablesImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TournamentTeamRepository tournamentTeamRepository;

    @Test
    void tablesAcceptOnlyApprovedTeamsAndDoNotWriteTableIdOnConflict() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("tbl-wd-" + suffix + "@example.com", "Str0ngPass!");
        String approvedId = team(admin, "Север " + suffix);
        String withdrawnId = team(admin, "Юг " + suffix);
        String pendingId = team(admin, "Запад " + suffix);
        String rejectedId = team(admin, "Восток " + suffix);
        String tournamentId = tournament(admin, "Весна " + suffix);

        enter(admin, tournamentId, approvedId);
        approve(admin, tournamentId, approvedId);
        enter(admin, tournamentId, withdrawnId);
        approve(admin, tournamentId, withdrawnId);
        withdraw(admin, tournamentId, withdrawnId);
        enter(admin, tournamentId, pendingId);
        enter(admin, tournamentId, rejectedId);
        approve(admin, tournamentId, rejectedId);
        forceStatus(tournamentId, rejectedId, TournamentTeamStatus.REJECTED);

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tablesBody("Группа B", withdrawnId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Команда снята с турнира"));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tablesBody("Группа P", pendingId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Заявка ещё на рассмотрении"));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tablesBody("Группа R", rejectedId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Заявка отклонена"));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertNoTable(admin, tournamentId, approvedId, "APPROVED");
        assertNoTable(admin, tournamentId, withdrawnId, "WITHDRAWN");
        assertNoTable(admin, tournamentId, pendingId, "PENDING");
        assertNoTable(admin, tournamentId, rejectedId, "REJECTED");

        MvcResult placed = mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tablesBody("Группа A", approvedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Группа A"))
                .andExpect(jsonPath("$[0].teamIds.length()").value(1))
                .andReturn();
        String tableId = objectMapper.readTree(placed.getResponse().getContentAsString()).get(0).get("id").asText();
        JsonNode approved = findTeam(admin, tournamentId, approvedId);
        assertEquals(tableId, approved.path("tableId").asText());

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[
                                  {"name":"Группа A","teamIds":["%s"]},
                                  {"name":"Группа B","teamIds":["%s"]}
                                ]}
                                """.formatted(approvedId, withdrawnId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Команда снята с турнира"));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tableId))
                .andExpect(jsonPath("$[0].name").value("Группа A"))
                .andExpect(jsonPath("$[0].teamIds[0]").value(approvedId));
        assertEquals(tableId, findTeam(admin, tournamentId, approvedId).path("tableId").asText());
        assertNoTable(admin, tournamentId, withdrawnId, "WITHDRAWN");
        assertNoTable(admin, tournamentId, pendingId, "PENDING");
        assertNoTable(admin, tournamentId, rejectedId, "REJECTED");
    }

    @Test
    void calendarImportCsvRejectsAWithdrawnTeamAndRollsBack() throws Exception {
        Clubs clubs = clubs("csv-wd");
        String csv = """
                date,time,home,away
                2026-09-12,18:00,%s,%s
                2026-09-13,19:00,%s,%s
                """.formatted(clubs.northName, clubs.westName, clubs.southName, clubs.northName);
        mockMvc.perform(multipart("/api/v1/tournaments/" + clubs.tournamentId + "/calendar/import")
                        .file(csvFile(csv))
                        .header("Authorization", auth(clubs.admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Строка 3: команда «" + clubs.southName + "» снята с турнира, сначала верните её"));
        assertImportRolledBack(clubs);
    }

    @Test
    void calendarImportXlsxRejectsAWithdrawnTeamAndRollsBack() throws Exception {
        Clubs clubs = clubs("xlsx-wd");
        String[][] grid = new String[8][];
        grid[0] = new String[] {"date", "time", "home", "away"};
        grid[1] = new String[] {"2026-09-12", "18:00", clubs.northName, clubs.westName};
        grid[7] = new String[] {"2026-09-20", "19:00", clubs.southName, clubs.northName};
        mockMvc.perform(multipart("/api/v1/tournaments/" + clubs.tournamentId + "/calendar/import")
                        .file(new MockMultipartFile(
                                "file", "calendar.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx(grid)))
                        .header("Authorization", auth(clubs.admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Строка 8: команда «" + clubs.southName + "» снята с турнира, сначала верните её"));
        assertImportRolledBack(clubs);
    }

    @Test
    void calendarImportOfApprovedTeamsLeavesAWithdrawnTeamThatIsNotInTheFile() throws Exception {
        Clubs clubs = clubs("imp-ok");
        enter(clubs.admin, clubs.tournamentId, clubs.westId);
        approve(clubs.admin, clubs.tournamentId, clubs.westId);

        String csv = """
                date,time,home,away
                2026-09-12,18:00,%s,%s
                """.formatted(clubs.northName, clubs.westName);
        mockMvc.perform(multipart("/api/v1/tournaments/" + clubs.tournamentId + "/calendar/import")
                        .file(csvFile(csv))
                        .header("Authorization", auth(clubs.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        String[][] grid = new String[][] {
                {"date", "time", "home", "away"},
                {"2026-09-13", "19:00", clubs.westName, clubs.northName}
        };
        mockMvc.perform(multipart("/api/v1/tournaments/" + clubs.tournamentId + "/calendar/import")
                        .file(new MockMultipartFile(
                                "file", "calendar.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx(grid)))
                        .header("Authorization", auth(clubs.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertEquals("WITHDRAWN", findTeam(clubs.admin, clubs.tournamentId, clubs.southId).path("status").asText());
        assertEquals("WITHDRAWN", findTeam(clubs.admin, clubs.tournamentId, clubs.eastId).path("status").asText());
        assertEquals("APPROVED", findTeam(clubs.admin, clubs.tournamentId, clubs.northId).path("status").asText());
        assertEquals("APPROVED", findTeam(clubs.admin, clubs.tournamentId, clubs.westId).path("status").asText());
        mockMvc.perform(get("/api/v1/tournaments/" + clubs.tournamentId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private void assertImportRolledBack(Clubs clubs) throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + clubs.tournamentId + "/teams")
                        .header("Authorization", auth(clubs.admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + clubs.westId + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + clubs.northId + "')].status", contains("APPROVED")))
                .andExpect(jsonPath("$[?(@.teamId=='" + clubs.southId + "')].status", contains("WITHDRAWN")))
                .andExpect(jsonPath("$[?(@.teamId=='" + clubs.eastId + "')].status", contains("WITHDRAWN")));
        mockMvc.perform(get("/api/v1/tournaments/" + clubs.tournamentId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void assertNoTable(String admin, String tournamentId, String teamId, String status) throws Exception {
        JsonNode team = findTeam(admin, tournamentId, teamId);
        assertNotNull(team);
        assertEquals(status, team.path("status").asText());
        assertTrue(team.path("tableId").isNull() || team.path("tableId").isMissingNode());
    }

    private Clubs clubs(String prefix) throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin(prefix + "-" + suffix + "@example.com", "Str0ngPass!");
        String northName = "Север " + suffix;
        String westName = "Запад " + suffix;
        String southName = "Юг " + suffix;
        String northId = team(admin, northName);
        String westId = team(admin, westName);
        String southId = team(admin, southName);
        String eastId = team(admin, "Восток " + suffix);
        String tournamentId = tournament(admin, "Весна " + suffix);
        enter(admin, tournamentId, northId);
        approve(admin, tournamentId, northId);
        enter(admin, tournamentId, southId);
        approve(admin, tournamentId, southId);
        withdraw(admin, tournamentId, southId);
        enter(admin, tournamentId, eastId);
        approve(admin, tournamentId, eastId);
        withdraw(admin, tournamentId, eastId);
        return new Clubs(admin, tournamentId, northId, westId, southId, eastId, northName, westName, southName);
    }

    private static String tablesBody(String name, String teamId) {
        return """
                {"tables":[{"name":"%s","teamIds":["%s"]}]}
                """.formatted(name, teamId);
    }

    private void forceStatus(String tournamentId, String teamId, TournamentTeamStatus status) {
        TournamentTeam entry = tournamentTeamRepository
                .findByTournamentIdAndTeamId(UUID.fromString(tournamentId), UUID.fromString(teamId))
                .orElseThrow();
        entry.setStatus(status);
        entry.setApprovedAt(null);
        entry.setTableId(null);
        tournamentTeamRepository.saveAndFlush(entry);
    }

    private JsonNode findTeam(String admin, String tournamentId, String teamId) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode team : objectMapper.readTree(listed.getResponse().getContentAsString())) {
            if (teamId.equals(team.path("teamId").asText())) {
                return team;
            }
        }
        return null;
    }

    private static MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "calendar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] xlsx(String[][] grid) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            for (int r = 0; r < grid.length; r++) {
                if (grid[r] == null) {
                    continue;
                }
                var row = sheet.createRow(r);
                for (int c = 0; c < grid[r].length; c++) {
                    row.createCell(c).setCellValue(grid[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String team(String admin, String name) throws Exception {
        String captain = player("cap-" + System.nanoTime() + "@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, Math.min(2, name.length())), captain)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String player(String email) throws Exception {
        String token = registerAndLogin(email, "Str0ngPass!", "CAPTAIN", "https://example.com/p.jpg");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Кап","lastName":"Игрок","jerseyNumber":9,"position":"FW"}
                                """))
                .andExpect(status().isOk());
        MvcResult me = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();
    }

    private String tournament(String admin, String name) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        String sportId = objectMapper.readTree(sports.getResponse().getContentAsString()).get(0).get("id").asText();
        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sportId":"%s","seasonYear":2026,"format":"ROUND_ROBIN","status":"REGISTRATION"}
                                """.formatted(name, sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void enter(String admin, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
    }

    private void approve(String admin, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    private void withdraw(String admin, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());
    }

    private record Clubs(
            String admin,
            String tournamentId,
            String northId,
            String westId,
            String southId,
            String eastId,
            String northName,
            String westName,
            String southName
    ) {
    }
}
