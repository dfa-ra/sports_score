package com.studentleague.matches;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MalformedIdIntegrationTest extends AbstractIntegrationTest {

    @Test
    void nonUuidPathIsBadRequestOnPublicResources() throws Exception {
        assertBadId("/api/v1/matches/999999/events");
        assertBadId("/api/v1/teams/not-a-uuid");
        assertBadId("/api/v1/players/999999");
        assertBadId("/api/v1/tournaments/999999");
    }

    @Test
    void unknownMatchUuidIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/matches/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Матч не найден"));
    }

    private void assertBadId(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Некорректный идентификатор"));
    }
}
