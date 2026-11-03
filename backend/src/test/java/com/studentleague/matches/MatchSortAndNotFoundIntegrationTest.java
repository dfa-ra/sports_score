package com.studentleague.matches;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchSortAndNotFoundIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anonymousUnknownSortIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/matches").param("sort", "foo,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Некорректное значение"))
                .andExpect(jsonPath("$.details[0].field").value("sort"))
                .andExpect(jsonPath("$.details[0].message").value("Некорректное значение"));
    }

    @Test
    void anonymousKnownSortIsOk() throws Exception {
        mockMvc.perform(get("/api/v1/matches").param("sort", "scheduledAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void nonUuidMatchPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/matches/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Матч не найден"));
    }

    @Test
    void unknownMatchUuidIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/matches/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Матч не найден"));
    }
}
