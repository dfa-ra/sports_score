package com.studentleague.teams;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamLookupIntegrationTest extends AbstractIntegrationTest {

    @Test
    void invalidTeamIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/teams/new"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Некорректный идентификатор"));
        mockMvc.perform(get("/api/v1/teams/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Некорректный идентификатор"));
    }

    @Test
    void unknownTeamIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/teams/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Команда не найдена"));
    }
}
