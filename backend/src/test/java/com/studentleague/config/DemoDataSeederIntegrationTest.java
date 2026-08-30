package com.studentleague.config;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class DemoDataSeederIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-long-enough-for-hs256-algorithms-123456");
        registry.add("app.jwt.access-expiration-ms", () -> "900000");
        registry.add("app.jwt.refresh-expiration-ms", () -> "604800000");
        registry.add("app.rate-limit.auth-requests-per-minute", () -> "1000");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("app.auth.auto-approve-roles", () -> "true");
        registry.add("app.demo-data.enabled", () -> "true");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @Test
    void emptyDatabaseGetsAPlayableLeague() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournament.name").value("KRONBARS Cup 2026"))
                .andExpect(jsonPath("$.standings.length()").value(8))
                .andExpect(jsonPath("$.scorers.length()").value(5))
                .andExpect(jsonPath("$.assists.length()").value(5));

        mockMvc.perform(get("/api/v1/statistics/goalkeepers").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cleanSheets").isNumber());
    }
}
