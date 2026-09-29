package com.studentleague.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleCertIdTokenParser implements GoogleIdTokenParser {

    private static final Logger log = LoggerFactory.getLogger(GoogleCertIdTokenParser.class);

    private final Object lock = new Object();
    private volatile List<String> cachedAudiences = List.of();
    private volatile GoogleIdTokenVerifier verifier;

    @Override
    public GoogleIdentity parse(String idToken, List<String> audiences) {
        try {
            GoogleIdToken parsed = verifierFor(audiences).verify(idToken);
            if (parsed == null) {
                return null;
            }
            GoogleIdToken.Payload payload = parsed.getPayload();
            return new GoogleIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    stringClaim(payload, "given_name"),
                    stringClaim(payload, "family_name"),
                    stringClaim(payload, "picture")
            );
        } catch (Exception ex) {
            log.warn("Google ID token was rejected ({})", ex.getClass().getSimpleName());
            return null;
        }
    }

    private GoogleIdTokenVerifier verifierFor(List<String> audiences) {
        List<String> expected = List.copyOf(audiences);
        GoogleIdTokenVerifier current = verifier;
        if (current != null && cachedAudiences.equals(expected)) {
            return current;
        }
        synchronized (lock) {
            if (verifier == null || !cachedAudiences.equals(expected)) {
                verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(expected)
                        .build();
                cachedAudiences = expected;
            }
            return verifier;
        }
    }

    private static String stringClaim(GoogleIdToken.Payload payload, String name) {
        Object value = payload.get(name);
        return value instanceof String text ? text : null;
    }
}
