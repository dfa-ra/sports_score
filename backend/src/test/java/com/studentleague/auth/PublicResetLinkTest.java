package com.studentleague.auth;

import com.studentleague.auth.service.PublicResetLink;
import com.studentleague.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicResetLinkTest {

    @Test
    void buildsResetLinkOnConfiguredHost() {
        String token = "abcDEF123_-token";
        String link = PublicResetLink.build("https://itmoliga.ru/", token);
        URI uri = URI.create(link);

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("itmoliga.ru");
        assertThat(uri.getPath()).isEqualTo("/reset-password");
        assertThat(uri.getRawUserInfo()).isNull();
        assertThat(URLDecoder.decode(uri.getRawQuery().substring("token=".length()), StandardCharsets.UTF_8))
                .isEqualTo(token);
    }

    @Test
    void rejectsOpenRedirectBases() {
        assertThatThrownBy(() -> PublicResetLink.build("javascript:alert(1)", "token"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PublicResetLink.build("//evil.example", "token"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PublicResetLink.build("https://user:secret@itmoliga.ru", "token"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PublicResetLink.build("https://itmoliga.ru\r\nLocation: https://evil.example", "token"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tokenCannotChangeHost() {
        String link = PublicResetLink.build("https://itmoliga.ru", "abc@https://evil.example/phish");
        URI uri = URI.create(link);
        assertThat(uri.getHost()).isEqualTo("itmoliga.ru");
        assertThat(uri.getRawQuery()).doesNotContain("@");
        assertThat(uri.getRawQuery()).contains("token=");
    }
}
