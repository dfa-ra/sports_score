package com.studentleague.auth.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String email, String resetUrl) {
        if (email == null || email.isBlank() || email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid reset recipient");
        }
        if (resetUrl == null || resetUrl.isBlank() || resetUrl.indexOf('\r') >= 0 || resetUrl.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid reset link");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Восстановление пароля");
            helper.setText("""
                    Здравствуйте.

                    Чтобы задать новый пароль, откройте ссылку. Она действует 1 час:
                    %s

                    Если вы не запрашивали сброс, проигнорируйте это письмо.
                    """.formatted(resetUrl));
            mailSender.send(message);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Password reset email failed", ex);
        }
    }
}
