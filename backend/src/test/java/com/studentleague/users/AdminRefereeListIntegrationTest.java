package com.studentleague.users;

import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.entity.User;
import com.studentleague.users.entity.UserRoleAssignment;
import com.studentleague.users.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRefereeListIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Test
    void listsRefereesThatTheUserDirectoryPageWouldHide() throws Exception {
        String token = "refs" + Long.toString(System.nanoTime(), 36);
        String adminToken = createAdminAndLogin("admin-" + token + "@example.com", "Str0ngPass!");

        UUID roleOnlyId = saveUser(
                "early." + token + "@example.com",
                "Илья" + token,
                "Скрытов" + token,
                Role.REFEREE
        );
        Thread.sleep(30);
        UUID assignedId = saveUser(
                "marked." + token + "@example.com",
                "Мария" + token,
                "Лайнсмен" + token,
                Role.PLAYER
        );
        approveReferee(assignedId);
        Thread.sleep(30);
        for (int i = 0; i < 21; i++) {
            saveUser("later-" + token + "-" + i + "@example.com", "Иван", "Петров" + i, Role.FAN);
        }
        UUID fanId = saveUser("fan." + token + "@example.com", "Зритель" + token, "Болельщик" + token, Role.FAN);

        String userPage = mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(userPage.contains(roleOnlyId.toString()), "old user page must hide the earlier referee");
        assertFalse(userPage.contains(assignedId.toString()), "old user page must hide the second referee");

        String referees = mockMvc.perform(get("/api/v1/admin/referees")
                        .param("q", token)
                        .param("size", "20")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(referees.contains(roleOnlyId.toString()), "role-only referee must be listed");
        assertTrue(referees.contains(assignedId.toString()), "approved referee assignment must be listed");
        assertFalse(referees.contains(fanId.toString()), "a fan must not appear in the referee list");

        assertFound(adminToken, "Скрытов" + token, roleOnlyId, assignedId);
        assertFound(adminToken, "Мария" + token, assignedId, roleOnlyId);
        assertFound(adminToken, "marked." + token, assignedId, roleOnlyId);
    }

    private void assertFound(String adminToken, String query, UUID expected, UUID other) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/referees")
                        .param("q", query)
                        .param("size", "20")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(body.contains(expected.toString()), "query '" + query + "' should return the referee");
        assertFalse(body.contains(other.toString()), "query '" + query + "' should not return the other referee");
    }

    private UUID saveUser(String email, String firstName, String lastName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-login");
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        return userRepository.saveAndFlush(user).getId();
    }

    private void approveReferee(UUID userId) {
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRole(Role.REFEREE);
        assignment.setStatus(RoleStatus.APPROVED);
        assignment.setRequestedAt(Instant.now());
        assignment.setReviewedAt(Instant.now());
        assignmentRepository.saveAndFlush(assignment);
    }
}
