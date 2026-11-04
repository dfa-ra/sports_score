package com.studentleague.teams;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamMembershipConflictIntegrationTest extends AbstractIntegrationTest {

    @Test
    void sameClubStillConflictsAndASecondClubWithoutASharedTournamentDoesNot() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("mem-admin-" + suffix + "@example.com", "Str0ngPass!");
        String playerId = player("mem-pl-" + suffix + "@example.com", "PLAYER");
        String northCaptainId = player("mem-north-" + suffix + "@example.com", "CAPTAIN");
        String southEmail = "mem-south-" + suffix + "@example.com";
        String southCaptainId = player(southEmail, "CAPTAIN");

        String northId = createTeam(admin, "Север " + suffix, northCaptainId);
        String southId = createTeam(admin, "Юг " + suffix, southCaptainId);
        String southToken = loginOnly(southEmail, "Str0ngPass!");

        mockMvc.perform(post("/api/v1/teams/" + northId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(playerId));

        mockMvc.perform(post("/api/v1/teams/" + southId + "/members")
                        .header("Authorization", auth(southToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(southId))
                .andExpect(jsonPath("$.playerId").value(playerId));

        mockMvc.perform(post("/api/v1/teams/" + southId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Игрок уже в составе этой команды"));

        mockMvc.perform(post("/api/v1/teams/" + northId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Игрок уже в составе этой команды"));

        mockMvc.perform(delete("/api/v1/teams/" + northId + "/members/" + playerId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + southId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].playerId", hasItem(playerId)));
    }

    @Test
    void captainOfASecondClubIsAllowedAndADisbandedClubDoesNotHoldThePlayer() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String admin = createAdminAndLogin("bind-admin-" + suffix + "@example.com", "Str0ngPass!");
        String playerId = player("bind-pl-" + suffix + "@example.com", "PLAYER");
        String northCaptainId = player("bind-north-" + suffix + "@example.com", "CAPTAIN");
        String eastCaptainId = player("bind-east-" + suffix + "@example.com", "CAPTAIN");

        String northId = createTeam(admin, "Север " + suffix, northCaptainId);
        mockMvc.perform(post("/api/v1/teams/" + northId + "/members")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Запад %s","shortName":"ЗА","captainPlayerId":"%s"}
                                """.formatted(suffix, playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.captainId").value(playerId));

        String eastId = createTeam(admin, "Восток " + suffix, eastCaptainId);
        mockMvc.perform(put("/api/v1/teams/" + eastId + "/captain")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));

        mockMvc.perform(delete("/api/v1/teams/" + northId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + northId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/teams/" + eastId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));
    }

    private String player(String email, String role) throws Exception {
        String token = registerAndLogin(email, "Str0ngPass!", role, "https://example.com/p.jpg");
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Игрок","lastName":"Состав","jerseyNumber":9,"position":"FW"}
                                """))
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
                                """.formatted(name, name.substring(0, 2), captainId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String memberBody(String playerId) {
        return """
                {"playerId":"%s"}
                """.formatted(playerId);
    }
}
