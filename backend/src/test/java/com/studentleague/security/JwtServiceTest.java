package com.studentleague.security;

import com.studentleague.config.AppProperties;
import com.studentleague.users.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-algorithms-123456", 60_000, 3_600_000),
                new AppProperties.RateLimit(30),
                new AppProperties.Redis(false),
                new AppProperties.LocalStorage("./data/uploads-test", "/media")
        );
        jwtService = new JwtService(properties);
    }

    @Test
    void createsAndParsesAccessToken() {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(),
                "fan@example.com",
                "hash",
                Role.FAN,
                true
        );

        String token = jwtService.createAccessToken(principal);
        JwtService.ParsedToken parsed = jwtService.parse(token);

        assertThat(parsed.userId()).isEqualTo(principal.getId());
        assertThat(parsed.email()).isEqualTo("fan@example.com");
        assertThat(parsed.role()).isEqualTo("FAN");
    }

    @Test
    void rejectsInvalidToken() {
        assertThatThrownBy(() -> jwtService.parse("not.a.valid.token"))
                .isInstanceOf(RuntimeException.class);
    }
}
