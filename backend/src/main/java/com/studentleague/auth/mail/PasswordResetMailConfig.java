package com.studentleague.auth.mail;

import com.studentleague.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class PasswordResetMailConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(AppProperties appProperties) {
        AppProperties.Mail mail = appProperties.mail();
        if (mail == null || mail.host() == null || mail.host().isBlank()
                || mail.from() == null || mail.from().isBlank()) {
            return new LoggingPasswordResetMailer();
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host().trim());
        sender.setPort(mail.port() > 0 ? mail.port() : 587);
        if (mail.username() != null && !mail.username().isBlank()) {
            sender.setUsername(mail.username().trim());
        }
        if (mail.password() != null && !mail.password().isBlank()) {
            sender.setPassword(mail.password());
        }
        Properties props = sender.getJavaMailProperties();
        boolean auth = mail.username() != null && !mail.username().isBlank();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(mail.starttls()));
        props.put("mail.smtp.starttls.required", Boolean.toString(mail.starttls()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return new SmtpPasswordResetMailer(sender, mail.from().trim());
    }
}
