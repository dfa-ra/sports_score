package com.studentleague.users;

import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserSearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void searchMatchesEmailAndLastNameBeyondTheFirstPage() throws Exception {
        String token = "search" + Long.toString(System.nanoTime(), 36);
        String email = "findme." + token + "@example.com";
        String lastName = "Кравцов" + token;
        String firstName = "Роман" + token;

        String adminToken = createAdminAndLogin("admin-" + token + "@example.com", "Str0ngPass!");
        UUID targetId = saveUser(email, firstName, lastName);
        Thread.sleep(30);
        for (int i = 0; i < 21; i++) {
            saveUser("later-" + token + "-" + i + "@example.com", "Иван", "Петров" + i);
        }

        String firstPage = mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(firstPage.contains(targetId.toString()), "target must sit outside the first page");

        String blank = mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "  ")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(blank.contains(targetId.toString()));

        assertFound(adminToken, lastName, targetId);
        assertFound(adminToken, email.substring(0, email.indexOf('@')), targetId);
        assertFound(adminToken, firstName, targetId);
    }

    private void assertFound(String adminToken, String query, UUID targetId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", query)
                        .param("size", "20")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(targetId.toString()), "query '" + query + "' should return the user");
    }

    private UUID saveUser(String email, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-login");
        user.setRole(Role.FAN);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        return userRepository.saveAndFlush(user).getId();
    }
}
