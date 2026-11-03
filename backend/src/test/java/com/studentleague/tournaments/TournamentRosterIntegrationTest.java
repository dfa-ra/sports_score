package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TournamentRosterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TournamentTeamRepository tournamentTeamRepository;

    @Test
    void playerJoinsTwoClubsInDifferentTournamentsAndCanAppearForEach() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rost-admin-" + suffix + "@example.com", "Str0ngPass!");
        String playerId = player("rost-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("rost-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("rost-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String westCaptain = player("rost-w-" + suffix + "@example.com", "CAPTAIN", "Запад", "Капитан");
        String eastCaptain = player("rost-e-" + suffix + "@example.com", "CAPTAIN", "Восток", "Капитан");

        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        String westId = createTeam(admin, "Запад " + suffix, westCaptain);
        String eastId = createTeam(admin, "Восток " + suffix, eastCaptain);

        String sportId = sportId(admin);
        String springId = tournament(admin, "Весна " + suffix, sportId);
        String autumnId = tournament(admin, "Осень " + suffix, sportId);
        enter(admin, springId, northId);
        enter(admin, springId, westId);
        enter(admin, autumnId, southId);
        enter(admin, autumnId, eastId);
        approve(admin, springId, northId);
        approve(admin, springId, westId);
        approve(admin, autumnId, southId);
        approve(admin, autumnId, eastId);

        addMember(admin, northId, playerId);
        addMember(admin, southId, playerId);

        String springMatch = match(admin, springId, northId, westId);
        String autumnMatch = match(admin, autumnId, southId, eastId);
        lineup(admin, springMatch, northId, playerId);
        lineup(admin, autumnMatch, southId, playerId);
        goalkeeper(admin, springMatch, northId, playerId);
        goalkeeper(admin, autumnMatch, southId, playerId);
        goal(admin, springMatch, northId, playerId);
        goal(admin, autumnMatch, southId, playerId);
    }

    @Test
    void addingToASecondClubInTheSameTournamentNamesTheOtherClub() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rost2-admin-" + suffix + "@example.com", "Str0ngPass!");
        String playerId = player("rost2-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("rost2-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String centreCaptain = player("rost2-c-" + suffix + "@example.com", "CAPTAIN", "Центр", "Капитан");

        String northName = "Север " + suffix;
        String tournamentName = "Весна " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String centreId = createTeam(admin, "Центр " + suffix, centreCaptain);
        String sportId = sportId(admin);
        String springId = tournament(admin, tournamentName, sportId);
        enter(admin, springId, northId);
        enter(admin, springId, centreId);
        addMember(admin, northId, playerId);

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Запас %s","shortName":"ЗП","captainPlayerId":"%s"}
                                """.formatted(suffix, playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.captainId").value(playerId));

        String conflict = "Игрок уже играет за «" + northName + "» в турнире «" + tournamentName + "»";
        mockMvc.perform(post("/api/v1/teams/" + centreId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(conflict));

        mockMvc.perform(put("/api/v1/teams/" + centreId + "/captain")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(conflict));
        mockMvc.perform(get("/api/v1/teams/" + centreId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(centreCaptain));

        mockMvc.perform(delete("/api/v1/teams/" + northId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/teams/" + centreId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(centreId));
    }

    @Test
    void enteringAClubNamesPlayersWhoAlreadyPlayForAnotherClub() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rost3-admin-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("rost3-a-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String sokolovId = player("rost3-b-" + suffix + "@example.com", "PLAYER", "Мария", "Соколов");
        String northCaptain = player("rost3-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("rost3-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");

        String northName = "Север " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        addMember(admin, northId, orlovId);
        addMember(admin, northId, sokolovId);
        addMember(admin, southId, orlovId);
        addMember(admin, southId, sokolovId);

        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(southId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Орлов Илья уже играет за «" + northName + "»; Соколов Мария уже играет за «" + northName + "»"));

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(northId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Команда уже заявлена на этот турнир"));
    }

    @Test
    void clubOutsideEveryTournamentCanStillAddAPlayer() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rost4-admin-" + suffix + "@example.com", "Str0ngPass!");
        String playerId = player("rost4-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("rost4-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String spareCaptain = player("rost4-z-" + suffix + "@example.com", "CAPTAIN", "Запас", "Капитан");

        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String spareId = createTeam(admin, "Запас " + suffix, spareCaptain);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        addMember(admin, northId, playerId);

        mockMvc.perform(post("/api/v1/teams/" + spareId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(spareId))
                .andExpect(jsonPath("$.playerId").value(playerId));
    }

    @Test
    void approvingAPendingEntrySucceedsWhenNobodyElseHoldsThePlayer() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("appr-ok-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("appr-ok-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String southCaptain = player("appr-ok-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, southId);

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.teamId").value(southId));
    }

    @Test
    void approvingAPendingEntryConflictsWhenAPlayerJoinedAnotherEnteredClub() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("appr-no-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("appr-no-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("appr-no-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("appr-no-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northName = "Север " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, southId);
        addMember(admin, northId, orlovId);
        // North is entered without the register check: that is the hole approve must close.
        forceEntered(springId, northId);

        String conflict = "Орлов Илья уже играет за «" + northName + "»";
        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(conflict));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')].status", contains("PENDING")));
    }

    @Test
    void calendarImportFailsWhenAnActivePlayerAlreadyPlaysForAnEnteredClub() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("imp-no-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("imp-no-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("imp-no-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("imp-no-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String westCaptain = player("imp-no-w-" + suffix + "@example.com", "CAPTAIN", "Запад", "Капитан");
        String northName = "Север " + suffix;
        String southName = "Юг " + suffix;
        String westName = "Запад " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, southName, southCaptain);
        String westId = createTeam(admin, westName, westCaptain);
        addMember(admin, northId, orlovId);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        approve(admin, springId, northId);

        String csv = """
                date,time,home,away
                2026-09-12,18:00,%s,%s
                2026-09-13,19:00,%s,%s
                """.formatted(southName, westName, westName, northName);
        MockMultipartFile file = new MockMultipartFile(
                "file", "calendar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String conflict = "Строка 2: Орлов Илья уже играет за «" + northName + "»";
        mockMvc.perform(multipart("/api/v1/tournaments/" + springId + "/calendar/import")
                        .file(file)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(conflict));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "' && @.status=='APPROVED')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + westId + "' && @.status=='APPROVED')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("APPROVED")));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void calendarImportDoesNotApproveAPendingClubThatNowConflicts() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("imp-pend-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("imp-pend-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("imp-pend-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("imp-pend-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String westCaptain = player("imp-pend-w-" + suffix + "@example.com", "CAPTAIN", "Запад", "Капитан");
        String northName = "Север " + suffix;
        String southName = "Юг " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, southName, southCaptain);
        createTeam(admin, "Запад " + suffix, westCaptain);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, southId);
        addMember(admin, northId, orlovId);
        forceEntered(springId, northId);

        String csv = """
                date,time,home,away
                2026-09-12,18:00,%s,Запад %s
                """.formatted(southName, suffix);
        MockMultipartFile file = new MockMultipartFile(
                "file", "calendar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/tournaments/" + springId + "/calendar/import")
                        .file(file)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Строка 2: Орлов Илья уже играет за «" + northName + "»"));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')].status", contains("PENDING")));
    }

    @Test
    void withdrawingAClubKeepsTheRowAndLetsThePlayerEnterWithAnotherClub() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("wd-admin-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("wd-pl-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String northCaptain = player("wd-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("wd-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        addMember(admin, northId, orlovId);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        approve(admin, springId, northId);

        mockMvc.perform(delete("/api/v1/tournaments/" + springId + "/teams/" + northId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("WITHDRAWN")))
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].id").isNotEmpty());

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(southId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.teamId").value(southId));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("WITHDRAWN")));
    }

    @Test
    void publicTeamListHidesWithdrawnEntries() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("pub-admin-" + suffix + "@example.com", "Str0ngPass!");
        String fan = registerAndLogin("pub-fan-" + suffix + "@example.com", "Str0ngPass!");
        String northCaptain = player("pub-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("pub-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        enter(admin, springId, southId);
        approve(admin, springId, northId);
        approve(admin, springId, southId);
        withdraw(admin, springId, southId);

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("APPROVED")))
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')]").isEmpty());
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(fan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("APPROVED")));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')].status", contains("WITHDRAWN")));
    }

    @Test
    void restoringAWithdrawnEntryConflictsAndLeavesItWithdrawn() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rst-no-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("rst-no-a-" + suffix + "@example.com", "PLAYER", "Илья", "Орлов");
        String sokolovId = player("rst-no-b-" + suffix + "@example.com", "PLAYER", "Мария", "Соколов");
        String northCaptain = player("rst-no-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("rst-no-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northName = "Север " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        addMember(admin, southId, orlovId);
        addMember(admin, southId, sokolovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, southId);
        approve(admin, springId, southId);
        withdraw(admin, springId, southId);
        addMember(admin, northId, orlovId);
        addMember(admin, northId, sokolovId);
        enter(admin, springId, northId);
        approve(admin, springId, northId);

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/restore")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Орлов Илья уже играет за «" + northName + "»; Соколов Мария уже играет за «" + northName + "»"));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')].status", contains("WITHDRAWN")));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')]").isEmpty());
    }

    @Test
    void approvingWithdrawnOrRejectedStaysOutOfTheTable() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("appr-st-" + suffix + "@example.com", "Str0ngPass!");
        String northCaptain = player("appr-st-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("appr-st-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        enter(admin, springId, southId);
        approve(admin, springId, northId);
        approve(admin, springId, southId);
        withdraw(admin, springId, northId);
        forceStatus(springId, southId, TournamentTeamStatus.REJECTED);

        String onlyPending = "Заявку можно одобрить только в статусе «На рассмотрении»";
        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + northId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(onlyPending));
        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(onlyPending));

        MvcResult listed = mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode teams = objectMapper.readTree(listed.getResponse().getContentAsString());
        JsonNode north = findTeam(teams, northId);
        JsonNode south = findTeam(teams, southId);
        assertNotNull(north);
        assertNotNull(south);
        assertEquals("WITHDRAWN", north.path("status").asText());
        assertTrue(north.path("tableId").isNull());
        assertEquals("REJECTED", south.path("status").asText());
        assertTrue(south.path("tableId").isNull());
    }

    @Test
    void restoredEntryApprovesIntoTheSameTableAsANormalApproval() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("rst-ok-" + suffix + "@example.com", "Str0ngPass!");
        String northCaptain = player("rst-ok-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("rst-ok-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String northId = createTeam(admin, "Север " + suffix, northCaptain);
        String southId = createTeam(admin, "Юг " + suffix, southCaptain);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        String tableId = soleTable(admin, springId);
        enter(admin, springId, northId);
        enter(admin, springId, southId);

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + northId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.tableId").value(tableId));
        approve(admin, springId, southId);
        withdraw(admin, springId, southId);

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Заявку можно одобрить только в статусе «На рассмотрении»"));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].id").value(tableId))
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId=='" + northId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId=='" + southId + "')]").isEmpty());

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/restore")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.teamId").value(southId));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "')].status", contains("PENDING")));

        mockMvc.perform(post("/api/v1/tournaments/" + springId + "/teams/" + southId + "/approve")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.tableId").value(tableId));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId=='" + northId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.tables[0].rows[?(@.teamId=='" + southId + "')]").isNotEmpty());
    }

    @Test
    void calendarImportXlsxNamesTheFileRowAndRollsBack() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("xlsx-no-" + suffix + "@example.com", "Str0ngPass!");
        String orlovId = player("xlsx-no-pl-" + suffix + "@example.com", "PLAYER", "Иван", "Иванов");
        String northCaptain = player("xlsx-no-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String southCaptain = player("xlsx-no-s-" + suffix + "@example.com", "CAPTAIN", "Юг", "Капитан");
        String westCaptain = player("xlsx-no-w-" + suffix + "@example.com", "CAPTAIN", "Запад", "Капитан");
        String northName = "Север " + suffix;
        String southName = "Юг " + suffix;
        String westName = "Запад " + suffix;
        String northId = createTeam(admin, northName, northCaptain);
        String southId = createTeam(admin, southName, southCaptain);
        String westId = createTeam(admin, westName, westCaptain);
        addMember(admin, northId, orlovId);
        addMember(admin, southId, orlovId);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));
        enter(admin, springId, northId);
        approve(admin, springId, northId);

        String[][] grid = new String[12][];
        grid[0] = new String[] {"date", "time", "home", "away"};
        grid[1] = new String[] {"2026-09-12", "18:00", westName, northName};
        grid[11] = new String[] {"2026-09-20", "19:00", southName, westName};
        MockMultipartFile file = new MockMultipartFile(
                "file", "calendar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx(grid));
        mockMvc.perform(multipart("/api/v1/tournaments/" + springId + "/calendar/import")
                        .file(file)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Строка 12: Иванов Иван уже играет за «" + northName + "»"));

        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/teams")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId=='" + southId + "' && @.status=='APPROVED')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + westId + "' && @.status=='APPROVED')]").isEmpty())
                .andExpect(jsonPath("$[?(@.teamId=='" + northId + "')].status", contains("APPROVED")));
        mockMvc.perform(get("/api/v1/tournaments/" + springId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void cleanCalendarImportReturnsOk() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("imp-ok-" + suffix + "@example.com", "Str0ngPass!");
        String northCaptain = player("imp-ok-n-" + suffix + "@example.com", "CAPTAIN", "Север", "Капитан");
        String westCaptain = player("imp-ok-w-" + suffix + "@example.com", "CAPTAIN", "Запад", "Капитан");
        String northName = "Север " + suffix;
        String westName = "Запад " + suffix;
        createTeam(admin, northName, northCaptain);
        createTeam(admin, westName, westCaptain);
        String springId = tournament(admin, "Весна " + suffix, sportId(admin));

        String csv = """
                date,time,home,away
                2026-09-12,18:00,%s,%s
                """.formatted(northName, westName);
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "calendar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/tournaments/" + springId + "/calendar/import")
                        .file(csvFile)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        String[][] grid = new String[][] {
                {"date", "time", "home", "away"},
                {"2026-09-13", "19:00", westName, northName}
        };
        MockMultipartFile xlsxFile = new MockMultipartFile(
                "file", "calendar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx(grid));
        mockMvc.perform(multipart("/api/v1/tournaments/" + springId + "/calendar/import")
                        .file(xlsxFile)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    private void forceEntered(String tournamentId, String teamId) {
        TournamentTeam entry = new TournamentTeam();
        entry.setTournamentId(UUID.fromString(tournamentId));
        entry.setTeamId(UUID.fromString(teamId));
        entry.setStatus(TournamentTeamStatus.APPROVED);
        entry.setApprovedAt(Instant.now());
        tournamentTeamRepository.saveAndFlush(entry);
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

    private void withdraw(String admin, String tournamentId, String teamId) throws Exception {
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/teams/" + teamId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());
    }

    private String soleTable(String admin, String tournamentId) throws Exception {
        MvcResult tables = mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"Группа A","teamIds":[]}]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(tables.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private static JsonNode findTeam(JsonNode teams, String teamId) {
        for (JsonNode team : teams) {
            if (teamId.equals(team.path("teamId").asText())) {
                return team;
            }
        }
        return null;
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

    private String player(String email, String role, String first, String last) throws Exception {
        String token = registerAndLogin(email, "Str0ngPass!", role, "https://example.com/p.jpg");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","jerseyNumber":9,"position":"FW"}
                                """.formatted(first, last)))
                .andExpect(status().isOk());
        MvcResult me = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTeam(String admin, String name, String captainId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"%s","captainPlayerId":"%s"}
                                """.formatted(name, name.substring(0, Math.min(2, name.length())), captainId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String sportId(String admin) throws Exception {
        MvcResult sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(sports.getResponse().getContentAsString());
        return list.get(0).get("id").asText();
    }

    private String tournament(String admin, String name, String sportId) throws Exception {
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

    private void addMember(String admin, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated());
    }

    private String match(String admin, String tournamentId, String homeId, String awayId) throws Exception {
        Instant kickoff = Instant.now().plus(1, ChronoUnit.DAYS);
        MvcResult result = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tournamentId":"%s","homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s"}
                                """.formatted(tournamentId, homeId, awayId, kickoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void lineup(String admin, String matchId, String teamId, String playerId) throws Exception {
        mockMvc.perform(put("/api/v1/matches/" + matchId + "/lineups")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","starterPlayerIds":["%s"]}
                                """.formatted(teamId, playerId)))
                .andExpect(status().isOk());
    }

    private void goalkeeper(String admin, String matchId, String teamId, String playerId) throws Exception {
        mockMvc.perform(put("/api/v1/admin/matches/" + matchId + "/goalkeeper")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s","playerId":"%s"}
                                """.formatted(teamId, playerId)))
                .andExpect(status().isOk());
    }

    private void goal(String admin, String matchId, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/matches/" + matchId + "/events")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"GOAL","teamId":"%s","playerId":"%s","minute":12}
                                """.formatted(teamId, playerId)))
                .andExpect(status().isCreated());
    }

    private static String memberBody(String playerId) {
        return """
                {"playerId":"%s"}
                """.formatted(playerId);
    }
}
