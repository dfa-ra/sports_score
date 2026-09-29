package com.studentleague.auth.google;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifier {

    private final AppProperties appProperties;
    private final GoogleIdTokenParser parser;

    public GoogleIdTokenVerifierAdapter(AppProperties appProperties, GoogleIdTokenParser parser) {
        this.appProperties = appProperties;
        this.parser = parser;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        List<String> audiences = clientIds();
        if (audiences.isEmpty()) {
            throw ApiException.serviceUnavailable("Вход через Google не настроен");
        }
        if (idToken == null || idToken.isBlank() || idToken.length() > 8192) {
            throw ApiException.unauthorized("Не удалось войти через Google");
        }
        GoogleIdentity identity = parser.parse(idToken.trim(), audiences);
        if (identity == null || identity.subject() == null || identity.subject().isBlank() || identity.subject().length() > 255) {
            throw ApiException.unauthorized("Не удалось войти через Google");
        }
        if (!identity.emailVerified() || identity.email() == null || !identity.email().contains("@")) {
            throw ApiException.unauthorized("Почта Google не подтверждена");
        }
        return identity;
    }

    private List<String> clientIds() {
        AppProperties.Google google = appProperties.google();
        if (google == null || google.clientId() == null || google.clientId().isBlank()) {
            return List.of();
        }
        return Arrays.stream(google.clientId().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
