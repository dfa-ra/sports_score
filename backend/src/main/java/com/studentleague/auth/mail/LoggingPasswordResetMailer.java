package com.studentleague.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when SMTP is not configured. The reset URL is intentionally not logged.
 */
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

    @Override
    public void sendResetLink(String email, String resetUrl) {
        if (resetUrl == null || resetUrl.isBlank()) {
            log.warn("Password reset was not sent because the link was empty");
            return;
        }
        log.info("Mail is not configured; password reset email was not sent to {}", email);
    }
}
