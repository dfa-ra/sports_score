package com.studentleague.auth.google;

import java.util.List;

/**
 * Turns a Google ID token into identity claims.
 * The production implementation calls Google; tests supply a fake.
 */
public interface GoogleIdTokenParser {

    /**
     * @return verified claims, or null when the token is not valid for {@code audiences}
     */
    GoogleIdentity parse(String idToken, List<String> audiences);
}
