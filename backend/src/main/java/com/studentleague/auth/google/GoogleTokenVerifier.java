package com.studentleague.auth.google;

public interface GoogleTokenVerifier {

    GoogleIdentity verify(String idToken);
}
