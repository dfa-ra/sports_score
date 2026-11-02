package com.studentleague.security;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FanDirectoryAccessTest extends AbstractIntegrationTest {

    @Test
    void fanIsForbiddenOnUserAndRefereeDirectories() throws Exception {
        String fanToken = registerAndLogin("fan-dir-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        mockMvc.perform(get("/api/v1/users").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/referees").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/referees").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/role-requests").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/gallery").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/matches").header("Authorization", auth(fanToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingApiPathIsNotAServerError() throws Exception {
        String adminToken = createAdminAndLogin("missing-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(get("/api/v1/users").header("Authorization", auth(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/matches").header("Authorization", auth(adminToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
