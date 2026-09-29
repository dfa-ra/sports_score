package com.studentleague.auth.google;

public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName,
        String pictureUrl
) {
}
