package com.studentleague.auth.google;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierAdapterTest {

    @Mock
    private GoogleIdTokenParser parser;

    @Test
    void blankClientIdDoesNotCallParser() {
        GoogleIdTokenVerifierAdapter adapter = newAdapter("  ");

        assertThatThrownBy(() -> adapter.verify("header.payload.sig"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        verifyNoInteractions(parser);
    }

    @Test
    void unverifiedEmailIsRejected() {
        when(parser.parse(eq("token"), any())).thenReturn(
                new GoogleIdentity("sub", "fan@gmail.com", false, "Анна", "Смирнова", null));
        GoogleIdTokenVerifierAdapter adapter = newAdapter("web-client");

        assertThatThrownBy(() -> adapter.verify("token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("не подтверждена");
    }

    @Test
    void invalidTokenIsRejected() {
        when(parser.parse(eq("bad"), any())).thenReturn(null);

        assertThatThrownBy(() -> newAdapter("web-client").verify("bad"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Не удалось войти через Google");
    }

    @Test
    void verifiedTokenReturnsIdentityAndPassesAudiences() {
        when(parser.parse(eq("good"), eq(List.of("web-client", "ios-client")))).thenReturn(
                new GoogleIdentity("sub-1", "fan@gmail.com", true, "Анна", "Смирнова", "https://example.com/a.png"));

        GoogleIdentity identity = newAdapter(" web-client, ios-client ").verify("good");

        assertThat(identity.subject()).isEqualTo("sub-1");
        assertThat(identity.email()).isEqualTo("fan@gmail.com");
        assertThat(identity.emailVerified()).isTrue();
        verify(parser, never()).parse(eq("good"), eq(List.of()));
    }

    private GoogleIdTokenVerifierAdapter newAdapter(String clientId) {
        return new GoogleIdTokenVerifierAdapter(properties(clientId), parser);
    }

    private static AppProperties properties(String clientId) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-algorithms-123456", 60_000, 3_600_000),
                new AppProperties.RateLimit(30),
                new AppProperties.Redis(false),
                new AppProperties.LocalStorage("./data/uploads-test", "/media"),
                new AppProperties.Admin("", ""),
                new AppProperties.Auth(false),
                new AppProperties.DemoData(false),
                new AppProperties.Google(clientId),
                "http://localhost:5173",
                new AppProperties.Mail("", 587, "", "", "", true)
        );
    }
}
