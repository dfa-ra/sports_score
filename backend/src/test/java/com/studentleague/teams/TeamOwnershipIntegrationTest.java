package com.studentleague.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamOwnershipIntegrationTest extends AbstractIntegrationTest {

    @Test
    void captainCanManageRosterButOtherUserCannot() throws Exception {
        String captainEmail = "captain-" + System.nanoTime() + "@example.com";
        String otherEmail = "other-" + System.nanoTime() + "@example.com";
        String playerEmail = "player-" + System.nanoTime() + "@example.com";

        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!");
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        String playerToken = registerAndLogin(playerEmail, "Str0ngPass!");

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Cap","lastName":"Tain","jerseyNumber":1,"position":"PG"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Oth","lastName":"Er","jerseyNumber":2,"position":"SG"}
                                """))
                .andExpect(status().isOk());

        MvcResult playerProfile = mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Play","lastName":"Er","jerseyNumber":7,"position":"SF"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String playerId = objectMapper.readTree(playerProfile.getResponse().getContentAsString()).get("id").asText();

        // Re-login captain to get CAPTAIN role after team creation (role changes on create)
        MvcResult teamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Campus United","shortName":"CU"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Campus United"))
                .andReturn();
        String teamId = objectMapper.readTree(teamResult.getResponse().getContentAsString()).get("id").asText();

        assertThat(userRepository.findByEmailIgnoreCase(captainEmail).orElseThrow().getRole())
                .isEqualTo(Role.CAPTAIN);

        // Fresh token after role promotion
        captainToken = loginOnly(captainEmail, "Str0ngPass!");

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(playerId));

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"%s"}
                                """.formatted(playerId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacked Name"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", auth(captainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/players/" + playerId + "/card")
                        .header("Authorization", auth(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Play"))
                .andExpect(jsonPath("$.team.name").value("Campus United"))
                .andExpect(jsonPath("$.jerseyNumber").value(7));

        mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/" + playerId)
                        .header("Authorization", auth(captainToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanListAndUpdateUsers() throws Exception {
        String adminToken = createAdminAndLogin("admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String fanEmail = "fan-admin-" + System.nanoTime() + "@example.com";
        registerAndLogin(fanEmail, "Str0ngPass!");
        String fanId = userRepository.findByEmailIgnoreCase(fanEmail).orElseThrow().getId().toString();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/users/" + fanId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"REFEREE","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REFEREE"));

        String fanToken = loginOnly(fanEmail, "Str0ngPass!");
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sportsAreSeeded() throws Exception {
        String token = registerAndLogin("sports-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(get("/api/v1/sports").header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='FOOTBALL')]").exists())
                .andExpect(jsonPath("$[?(@.code=='BASKETBALL')]").exists());
    }

    private String loginOnly(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
