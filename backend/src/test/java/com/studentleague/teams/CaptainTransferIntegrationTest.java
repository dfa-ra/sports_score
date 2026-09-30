package com.studentleague.teams;

import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CaptainTransferIntegrationTest extends AbstractIntegrationTest {

    @Test
    void transferMovesCaptainAndAddMemberDoesNot() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("capx-admin-" + suffix + "@example.com", "Str0ngPass!");
        String captainEmail = "capx-cap-" + suffix + "@example.com";
        String playerEmail = "capx-pl-" + suffix + "@example.com";
        String outsiderEmail = "capx-out-" + suffix + "@example.com";

        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");
        String playerToken = registerAndLogin(playerEmail, "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String outsiderToken = registerAndLogin(outsiderEmail, "Str0ngPass!", "PLAYER", "https://example.com/o.jpg");

        String captainId = profile(captainToken, "Кап", "Итан", 1);
        String playerId = profile(playerToken, "Игрок", "Новый", 7);
        String outsiderId = profile(outsiderToken, "Чужой", "Игрок", 8);

        String teamId = createTeam(adminToken, "Передача " + suffix, captainId);
        captainToken = loginOnly(captainEmail, "Str0ngPass!");

        addMember(captainToken, teamId, playerId);

        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(captainId));
        mockMvc.perform(get("/api/v1/players/" + playerId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.captain").value(false))
                .andExpect(jsonPath("$.captainTeams").isEmpty());

        mockMvc.perform(put("/api/v1/teams/" + teamId + "/captain")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(playerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/teams/" + teamId + "/captain")
                        .header("Authorization", auth(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(outsiderId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(captainId));

        mockMvc.perform(put("/api/v1/teams/" + teamId + "/captain")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));

        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));
        assertNotCaptain(captainEmail);
        assertCaptain(playerEmail);
        mockMvc.perform(get("/api/v1/players/" + captainId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainTeams").isEmpty());
        mockMvc.perform(get("/api/v1/players/" + playerId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.captain").value(true))
                .andExpect(jsonPath("$.captainTeams.length()").value(1))
                .andExpect(jsonPath("$.captainTeams[0].id").value(teamId))
                .andExpect(jsonPath("$.captainTeams[0].captain").value(true));

        addMember(playerToken, teamId, outsiderId);
        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));
        mockMvc.perform(get("/api/v1/players/" + outsiderId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.captain").value(false))
                .andExpect(jsonPath("$.captainTeams").isEmpty());

        mockMvc.perform(put("/api/v1/teams/" + teamId + "/captain")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(captainId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(jsonPath("$.captainId").value(playerId));

        mockMvc.perform(put("/api/v1/teams/" + teamId + "/captain")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(outsiderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(outsiderId));
        assertNotCaptain(playerEmail);
        assertCaptain(outsiderEmail);
        mockMvc.perform(get("/api/v1/players/" + playerId + "/card"))
                .andExpect(jsonPath("$.team.captain").value(false))
                .andExpect(jsonPath("$.captainTeams").isEmpty());
        mockMvc.perform(get("/api/v1/players/" + outsiderId + "/card"))
                .andExpect(jsonPath("$.captainTeams.length()").value(1))
                .andExpect(jsonPath("$.captainTeams[0].id").value(teamId));
        mockMvc.perform(get("/api/v1/players/" + captainId + "/card"))
                .andExpect(jsonPath("$.captainTeams").isEmpty());
    }

    @Test
    void transferKeepsRoleWhilePlayerStillCaptainsAnotherTeam() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = createAdminAndLogin("capy-admin-" + suffix + "@example.com", "Str0ngPass!");
        String captainEmail = "capy-cap-" + suffix + "@example.com";
        String playerEmail = "capy-pl-" + suffix + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");
        String playerToken = registerAndLogin(playerEmail, "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");
        String captainId = profile(captainToken, "Два", "Клуба", 4);
        String playerId = profile(playerToken, "Один", "Клуб", 5);

        String alphaId = createTeam(adminToken, "Alpha " + suffix, captainId);
        String betaId = createTeam(adminToken, "Beta " + suffix, captainId);
        captainToken = loginOnly(captainEmail, "Str0ngPass!");
        addMember(captainToken, alphaId, playerId);

        mockMvc.perform(put("/api/v1/teams/" + alphaId + "/captain")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(captainBody(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(playerId));

        mockMvc.perform(get("/api/v1/teams/" + betaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captainId").value(captainId));
        assertCaptain(captainEmail);
        assertCaptain(playerEmail);
        mockMvc.perform(get("/api/v1/players/" + captainId + "/card"))
                .andExpect(jsonPath("$.captainTeams.length()").value(1))
                .andExpect(jsonPath("$.captainTeams[0].id").value(betaId));
        mockMvc.perform(get("/api/v1/players/" + playerId + "/card"))
                .andExpect(jsonPath("$.captainTeams.length()").value(1))
                .andExpect(jsonPath("$.captainTeams[0].id").value(alphaId));
    }

    private void assertCaptain(String email) {
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CAPTAIN);
        assertThat(roleService.hasApproved(user.getId(), Role.CAPTAIN)).isTrue();
    }

    private void assertNotCaptain(String email) {
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isNotEqualTo(Role.CAPTAIN);
        assertThat(roleService.hasApproved(user.getId(), Role.CAPTAIN)).isFalse();
    }

    private String profile(String token, String first, String last, int number) throws Exception {
        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","jerseyNumber":%d,"position":"FW"}
                                """.formatted(first, last, number)))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTeam(String adminToken, String name, String captainId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shortName":"TM","captainPlayerId":"%s"}
                                """.formatted(name, captainId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String token, String teamId, String playerId) throws Exception {
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(playerId));
    }

    private static String captainBody(String playerId) {
        return """
                {"playerId":"%s"}
                """.formatted(playerId);
    }
}
