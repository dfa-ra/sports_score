package com.studentleague.config;

import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    private static final String PRIMARY = "admin@studentleague.local";
    private static final String E2E = "e2e-admin@example.com";
    private static final String OTHER = "second-admin@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

    private User primary;
    private User e2e;
    private User other;

    @BeforeEach
    void setUp() {
        primary = admin(PRIMARY);
        e2e = admin(E2E);
        other = admin(OTHER);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.findAll()).thenReturn(List.of(primary, e2e, other));
    }

    @Test
    void singleAdminEmailStillDemotesAnotherAdmin() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        runner(properties(PRIMARY, null), production()).run(null);

        verify(roleService).reject(other.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
        verify(roleService).reject(e2e.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
        verify(roleService, never()).reject(eq(primary.getId()), eq(Role.ADMIN), any());
    }

    @Test
    void e2eAdminEmailOnDevStandKeepsE2eAdmin() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        runner(properties(PRIMARY, E2E), devStand()).run(null);

        verify(roleService, never()).reject(eq(e2e.getId()), eq(Role.ADMIN), any());
        verify(roleService, never()).reject(eq(primary.getId()), eq(Role.ADMIN), any());
        verify(roleService).reject(other.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
    }

    @Test
    void e2eAdminEmailOnLocalDevProfileKeepsE2eAdmin() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        runner(properties(PRIMARY, E2E), environment("dev", "", "http://localhost:5173")).run(null);

        verify(roleService, never()).reject(eq(e2e.getId()), eq(Role.ADMIN), any());
        verify(roleService).reject(other.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
    }

    @Test
    void e2eAdminEmailOnProductionIsIgnored() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        runner(properties(PRIMARY, E2E), production()).run(null);

        verify(roleService).reject(e2e.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
        verify(roleService).reject(other.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
        verify(roleService, never()).reject(eq(primary.getId()), eq(Role.ADMIN), any());
    }

    @Test
    void productionPublicHostIgnoresE2eAdminWithoutComposeProject() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        Environment prodByUrl = environment("prod", "", "https://itmoliga.ru");
        runner(properties(PRIMARY, E2E), prodByUrl).run(null);

        verify(roleService).reject(e2e.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
    }

    @Test
    void devStandPublicHostKeepsE2eAdminWithoutComposeProject() {
        when(userRepository.findByEmailIgnoreCase(PRIMARY)).thenReturn(Optional.of(primary));
        Environment devByUrl = environment("prod", "", "http://144.31.153.52:3000");
        runner(properties(PRIMARY, E2E), devByUrl).run(null);

        verify(roleService, never()).reject(eq(e2e.getId()), eq(Role.ADMIN), any());
        verify(roleService).reject(other.getId(), Role.ADMIN, AdminBootstrapRunner.SINGLE_ADMIN_NOTE);
    }

    private AdminBootstrapRunner runner(AppProperties properties, Environment environment) {
        return new AdminBootstrapRunner(properties, userRepository, passwordEncoder, roleService, environment);
    }

    private static AppProperties properties(String adminEmail, String e2eEmail) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-algorithms-123456", 60_000, 3_600_000),
                new AppProperties.RateLimit(30),
                new AppProperties.Redis(false),
                new AppProperties.LocalStorage("./data/uploads-test", "/media"),
                new AppProperties.Admin(adminEmail, "secret", e2eEmail),
                new AppProperties.Auth(false),
                new AppProperties.DemoData(false),
                new AppProperties.Google(""),
                "http://localhost:5173",
                new AppProperties.Mail("", 587, "", "", "", true)
        );
    }

    private static Environment production() {
        return environment("prod", "studentleague-prod", "https://itmoliga.ru");
    }

    private static Environment devStand() {
        return environment("prod", "studentleague-dev", "http://144.31.153.52:3000");
    }

    private static Environment environment(String profile, String composeProject, String publicUrl) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        Map<String, Object> values = new HashMap<>();
        values.put("COMPOSE_PROJECT_NAME", composeProject);
        values.put("app.public-url", publicUrl);
        environment.getPropertySources().addFirst(new MapPropertySource("test-admin", values));
        return environment;
    }

    private static User admin(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        return user;
    }
}
