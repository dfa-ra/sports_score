package com.studentleague.security;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicSurfaceSecurityTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/actuator",
            "/actuator/health",
            "/actuator/info",
            "/actuator/env",
            "/h2-console"
    })
    void developerSurfacesAreClosedToEveryone(String path) throws Exception {
        int anonymous = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(anonymous).isIn(401, 403);

        String token = registerAndLogin("docs-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        int authenticated = mockMvc.perform(get(path).header("Authorization", auth(token)))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(authenticated).isIn(401, 403);
    }

    @Test
    void healthProbeStaysOnTheApplicationPath() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/tournaments",
            "/api/v1/matches",
            "/api/v1/teams",
            "/api/v1/players",
            "/api/v1/sports",
            "/api/v1/statistics/players",
            "/api/v1/statistics/teams"
    })
    void catalogIsPublicWithoutRegistration(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/players/me",
            "/api/v1/auth/me",
            "/api/v1/admin/users",
            "/api/v1/referee/matches"
    })
    void privateSurfacesStillNeedALogin(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
