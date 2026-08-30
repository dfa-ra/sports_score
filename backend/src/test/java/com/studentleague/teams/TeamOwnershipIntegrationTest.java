package com.studentleague.teams;

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
    void adminCreatesTeamAndCaptainManagesRoster() throws Exception {
        String adminToken = createAdminAndLogin("admin-own-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "captain-" + System.nanoTime() + "@example.com";
        String otherEmail = "other-" + System.nanoTime() + "@example.com";
        String playerEmail = "player-" + System.nanoTime() + "@example.com";

        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/cap.jpg");
        String otherToken = registerAndLogin(otherEmail, "Str0ngPass!");
        String playerToken = registerAndLogin(playerEmail, "Str0ngPass!", "PLAYER", "https://example.com/p.jpg");

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
        String captainPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(captainToken)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("id").asText();

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Campus United","shortName":"CU"}
                                """))
                .andExpect(status().isForbidden());

        MvcResult teamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Campus United","shortName":"CU","captainPlayerId":"%s","foundedOn":"2020-09-01"}
                                """.formatted(captainPlayerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Campus United"))
                .andExpect(jsonPath("$.foundedOn").value("2020-09-01"))
                .andReturn();
        String teamId = objectMapper.readTree(teamResult.getResponse().getContentAsString()).get("id").asText();

        assertThat(userRepository.findByEmailIgnoreCase(captainEmail).orElseThrow().getRole())
                .isEqualTo(Role.CAPTAIN);

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
    void adminCanCreateAndDisbandTeam() throws Exception {
        String adminToken = createAdminAndLogin("admin-team-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String captainEmail = "captain-disband-" + System.nanoTime() + "@example.com";
        String captainToken = registerAndLogin(captainEmail, "Str0ngPass!", "CAPTAIN", "https://example.com/c.jpg");

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", auth(captainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Cap","lastName":"Tain","jerseyNumber":1,"position":"PG"}
                                """))
                .andExpect(status().isOk());
        String captainPlayerId = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/players/me").header("Authorization", auth(captainToken)))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        MvcResult teamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Campus United","shortName":"CU","captainPlayerId":"%s"}
                                """.formatted(captainPlayerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disbanded").value(false))
                .andReturn();
        String teamId = objectMapper.readTree(teamResult.getResponse().getContentAsString()).get("id").asText();
        captainToken = loginOnly(captainEmail, "Str0ngPass!");

        mockMvc.perform(delete("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(captainToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbanded").value(true));

        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + teamId + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/teams").param("includeDisbanded", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + teamId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='" + teamId + "')].disbanded").value(org.hamcrest.Matchers.hasItem(true)));

        mockMvc.perform(put("/api/v1/teams/" + teamId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Still Alive"}
                                """))
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.role").value("REFEREE"))
                .andExpect(jsonPath("$.roles[?(@.role=='REFEREE' && @.status=='APPROVED')]").exists());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/users/" + fanId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["FAN","PLAYER","REFEREE"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REFEREE"))
                .andExpect(jsonPath("$.roles[?(@.role=='FAN' && @.status=='APPROVED')]").exists())
                .andExpect(jsonPath("$.roles[?(@.role=='PLAYER' && @.status=='APPROVED')]").exists())
                .andExpect(jsonPath("$.roles[?(@.role=='REFEREE' && @.status=='APPROVED')]").exists());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/users/" + fanId)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["FAN","PLAYER"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.roles[?(@.role=='PLAYER' && @.status=='APPROVED')]").exists())
                .andExpect(jsonPath("$.roles[?(@.role=='REFEREE' && @.status=='APPROVED')]").doesNotExist());

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
}
