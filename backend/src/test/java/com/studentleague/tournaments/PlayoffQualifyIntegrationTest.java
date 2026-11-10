package com.studentleague.tournaments;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayoffQualifyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TournamentTeamRepository tournamentTeamRepository;

    @Test
    void zakharExampleSavesAndABadPlaceDoesNotReplaceIt() throws Exception {
        String admin = createAdminAndLogin("pq-z-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        List<String> teamsA = addTeams(UUID.fromString(tournamentId), "A", 6);
        List<String> teamsB = addTeams(UUID.fromString(tournamentId), "B", 6);
        List<String> teamsC = addTeams(UUID.fromString(tournamentId), "C", 8);
        JsonNode tables = assign(admin, tournamentId, teamsA, teamsB, teamsC);
        String groupA = tables.get(0).get("id").asText();
        String groupB = tables.get(1).get("id").asText();
        String groupC = tables.get(2).get("id").asText();

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zakharBody(groupA, groupB, groupC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.bracketBuilt").value(false))
                .andExpect(jsonPath("$.derived.perStage.R8.direct").value(8))
                .andExpect(jsonPath("$.derived.perStage.R8.fromPrev").value(0))
                .andExpect(jsonPath("$.derived.perStage.R8.total").value(8))
                .andExpect(jsonPath("$.derived.perStage.R8.powerOfTwo").value(true))
                .andExpect(jsonPath("$.derived.perStage.QF.direct").value(4))
                .andExpect(jsonPath("$.derived.perStage.QF.fromPrev").value(4))
                .andExpect(jsonPath("$.derived.perStage.QF.total").value(8))
                .andExpect(jsonPath("$.derived.perStage.QF.powerOfTwo").value(true))
                .andExpect(jsonPath("$.derived.perStage.SF.total").value(4))
                .andExpect(jsonPath("$.derived.perStage.F.total").value(2))
                .andExpect(jsonPath("$.groups[0].places[0].teamName").value(nullValue()));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualifyMarks.length()").value(12));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"groupId":"%s","placeFrom":1,"placeTo":7,"stage":"QF"}]}
                                """.formatted(groupA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_OUT_OF_RANGE"));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derived.perStage.R8.total").value(8));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[
                                  {"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"QF"},
                                  {"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"QF"},
                                  {"groupId":"%s","placeFrom":3,"placeTo":4,"stage":"R8"},
                                  {"groupId":"%s","placeFrom":3,"placeTo":4,"stage":"R8"},
                                  {"groupId":"%s","placeFrom":1,"placeTo":3,"stage":"R8"}
                                ]}
                                """.formatted(groupA, groupB, groupA, groupB, groupC)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_POWER_OF_TWO"))
                .andExpect(jsonPath("$.message", containsString("В 1/8 должно быть 8 команд")));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derived.perStage.QF.direct").value(4));
    }

    @Test
    void lockedBracketRejectsRuleEditsUntilRebuildIsConfirmed() throws Exception {
        String admin = createAdminAndLogin("pq-l-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        List<String> pair = new ArrayList<>();
        String groupId = group(admin, tournamentId, "Группа A", 2, pair);
        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff/places")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"places\":[1,2]}"))
                .andExpect(status().isOk());
        String kickoff = Instant.now().plusSeconds(3600).toString();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pairs":[{"homeTeamId":"%s","awayTeamId":"%s","scheduledAt":"%s","venue":"Зал"}]}
                                """.formatted(pair.get(0), pair.get(1), kickoff)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"SF"}]}
                                """.formatted(groupId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCKED"));

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff-qualify/rebuild")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/playoff-qualify/rebuild")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.bracketBuilt").value(false));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionStage").value("GROUPS"))
                .andExpect(jsonPath("$.rounds.length()").value(0));

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"SF"}]}
                                """.formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derived.perStage.SF.total").value(2))
                .andExpect(jsonPath("$.derived.perStage.SF.powerOfTwo").value(true));
    }

    @Test
    void qualifyEndpointIsAdminOnly() throws Exception {
        String admin = createAdminAndLogin("pq-a-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff-qualify"))
                .andExpect(status().isUnauthorized());
        String fan = registerAndLogin("pq-fan-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(fan)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tournamentWithoutGroupsSaysSo() throws Exception {
        String admin = createAdminAndLogin("pq-e-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String sportId = footballId(admin);
        String tournamentId = createTournament(admin, sportId);
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/playoff-qualify")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(0))
                .andExpect(jsonPath("$.rules.length()").value(0));
    }

    private String group(String admin, String tournamentId, String name, int size) throws Exception {
        return group(admin, tournamentId, name, size, null);
    }

    private String group(String admin, String tournamentId, String name, int size, List<String> teamIdsOut) throws Exception {
        List<String> teamIds = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            teamIds.add(addTeam(UUID.fromString(tournamentId), name + "-" + (index + 1) + "-" + System.nanoTime()));
        }
        if (teamIdsOut != null) {
            teamIdsOut.addAll(teamIds);
        }
        String ids = String.join("\",\"", teamIds);
        var result = mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"name":"%s","teamIds":["%s"]}]}
                                """.formatted(name, ids)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    /**
     * Tables replace the whole set, so the three Zakhar groups are written together.
     */
    private String zakharBody(String groupA, String groupB, String groupC) {
        return """
                {"rules":[
                  {"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"QF"},
                  {"groupId":"%s","placeFrom":3,"placeTo":4,"stage":"R8"},
                  {"groupId":"%s","placeFrom":5,"placeTo":6,"stage":"OUT"},
                  {"groupId":"%s","placeFrom":1,"placeTo":2,"stage":"QF"},
                  {"groupId":"%s","placeFrom":3,"placeTo":4,"stage":"R8"},
                  {"groupId":"%s","placeFrom":5,"placeTo":6,"stage":"OUT"},
                  {"groupId":"%s","placeFrom":1,"placeTo":4,"stage":"R8"},
                  {"groupId":"%s","placeFrom":5,"placeTo":8,"stage":"OUT"}
                ]}
                """.formatted(groupA, groupA, groupA, groupB, groupB, groupB, groupC, groupC);
    }

    private List<String> addTeams(UUID tournamentId, String prefix, int size) {
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            ids.add(addTeam(tournamentId, prefix + "-" + (index + 1) + "-" + System.nanoTime()));
        }
        return ids;
    }

    private JsonNode assign(String admin, String tournamentId, List<String> a, List<String> b, List<String> c) throws Exception {
        String body = """
                {"tables":[
                  {"name":"Группа A","teamIds":[%s]},
                  {"name":"Группа B","teamIds":[%s]},
                  {"name":"Группа C","teamIds":[%s]}
                ]}
                """.formatted(quoted(a), quoted(b), quoted(c));
        var result = mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/tables")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String quoted(List<String> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").reduce((left, right) -> left + "," + right).orElse("");
    }

    private String addTeam(UUID tournamentId, String name) {
        Team team = new Team();
        team.setName(name);
        team.setShortName("T");
        team = teamRepository.save(team);
        TournamentTeam entry = new TournamentTeam();
        entry.setTournamentId(tournamentId);
        entry.setTeamId(team.getId());
        entry.setStatus(TournamentTeamStatus.APPROVED);
        entry.setApprovedAt(Instant.now());
        tournamentTeamRepository.save(entry);
        return team.getId().toString();
    }

    private String footballId(String admin) throws Exception {
        var sports = mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(sports.getResponse().getContentAsString())) {
            if ("FOOTBALL".equals(node.get("code").asText())) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("FOOTBALL sport is missing");
    }

    private String createTournament(String admin, String sportId) throws Exception {
        var tournament = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Квалификация","sportId":"%s","seasonYear":2026,"format":"GROUPS_PLAYOFF","status":"REGISTRATION"}
                                """.formatted(sportId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(tournament.getResponse().getContentAsString()).get("id").asText();
    }
}
