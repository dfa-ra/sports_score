package com.studentleague.auth.mail;

public interface PasswordResetMailer {

    void sendResetLink(String email, String resetUrl);
}
