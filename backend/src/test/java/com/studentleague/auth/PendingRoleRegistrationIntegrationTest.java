package com.studentleague.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studentleague.common.exception.ApiException;
import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.repository.UserRoleAssignmentRepository;
import com.studentleague.users.service.RoleService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class PendingRoleRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Autowired
    private RoleService roleService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-long-enough-for-hs256-algorithms-123456");
        registry.add("app.jwt.access-expiration-ms", () -> "900000");
        registry.add("app.jwt.refresh-expiration-ms", () -> "604800000");
        registry.add("app.rate-limit.auth-requests-per-minute", () -> "1000");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("app.auth.auto-approve-roles", () -> "false");
        registry.add("app.demo-data.enabled", () -> "false");
        registry.add("app.google.client-id", () -> "test-google-client");
        registry.add("app.public-url", () -> "http://localhost:5173");
        registry.add("app.mail.host", () -> "");
        registry.add("app.mail.from", () -> "");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAYER", "CAPTAIN", "REFEREE"})
    void pendingApplicationStaysInactiveAndCannotBeSentAgain(String roleName) throws Exception {
        Role role = Role.valueOf(roleName);
        String email = roleName.toLowerCase() + "-" + System.nanoTime() + "@example.com";
        String photo = "/media/registration/photo.jpg";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!","firstName":"Иван","lastName":"Иванов","role":"%s","photoUrl":"%s"}
                                """.formatted(email, roleName, photo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FAN"))
                .andExpect(jsonPath("$.roles[?(@.role=='FAN' && @.status=='APPROVED')]").exists())
                .andExpect(jsonPath("$.roles[?(@.role=='%s' && @.status=='PENDING')]".formatted(roleName)).exists());

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.FAN);
        assertThatThrownBy(() -> roleService.requestRole(user, role, photo))
                .isInstanceOf(ApiException.class)
                .hasMessage("Заявка на роль «" + RoleService.roleTitle(role) + "» уже на рассмотрении");
        assertThat(assignmentRepository.findByUserIdAndRole(user.getId(), role).orElseThrow().getStatus())
                .isEqualTo(RoleStatus.PENDING);

        String token = login(email);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FAN"))
                .andExpect(jsonPath("$.roles[?(@.role=='%s' && @.status=='PENDING')]".formatted(roleName)).exists());

        if (role != Role.PLAYER) {
            return;
        }

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Иван","lastName":"Иванов","displayName":"Иван","jerseyNumber":7,"position":"Нападающий","avatarUrl":"/media/registration/photo.jpg"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Заявка на роль «Игрок» уже на рассмотрении"));

        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getRole()).isEqualTo(Role.FAN);
        assertThat(assignmentRepository.findByUserIdAndRole(user.getId(), Role.PLAYER).orElseThrow().getStatus())
                .isEqualTo(RoleStatus.PENDING);
    }

    private String login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPass!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
