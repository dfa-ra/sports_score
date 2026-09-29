package com.studentleague.auth.service;

import com.studentleague.common.exception.ApiException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class PublicResetLink {

    private PublicResetLink() {
    }

    public static String normalizeBase(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw ApiException.serviceUnavailable("APP_PUBLIC_URL не задан");
        }
        String trimmed = publicUrl.trim();
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\\') >= 0) {
            throw ApiException.serviceUnavailable("APP_PUBLIC_URL задан неверно");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw ApiException.serviceUnavailable("APP_PUBLIC_URL задан неверно");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw ApiException.serviceUnavailable("APP_PUBLIC_URL должен быть http или https");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw ApiException.serviceUnavailable("APP_PUBLIC_URL задан неверно");
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static String build(String publicUrl, String rawToken) {
        String base = normalizeBase(publicUrl);
        if (!isSafeToken(rawToken)) {
            throw ApiException.serviceUnavailable("Не удалось создать ссылку для сброса пароля");
        }
        String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return base + "/reset-password?token=" + encoded;
    }

    private static boolean isSafeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            return false;
        }
        for (int i = 0; i < rawToken.length(); i++) {
            char ch = rawToken.charAt(i);
            if (ch <= ' ' || ch == '"' || ch == '\\' || ch == '\'' || ch == '<' || ch == '>') {
                return false;
            }
        }
        return true;
    }
}
