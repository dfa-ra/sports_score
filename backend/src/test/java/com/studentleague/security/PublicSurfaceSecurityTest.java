package com.studentleague.security;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        mockMvc.perform(get(path))
                .andExpect(status().isForbidden());

        String token = registerAndLogin("docs-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(get(path).header("Authorization", auth(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthProbeStaysOnTheApplicationPath() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
