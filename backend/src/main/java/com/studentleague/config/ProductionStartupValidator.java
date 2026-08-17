package com.studentleague.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionStartupValidator implements ApplicationRunner {

    private final Environment environment;

    public ProductionStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = environment.getProperty("app.jwt.secret", "");
        if (secret == null || secret.length() < 32 || secret.contains("change-me")) {
            throw new IllegalStateException(
                    "Production requires a strong JWT_SECRET (at least 32 characters, not the example value)");
        }
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if (dbPassword == null || dbPassword.isBlank() || "studentleague".equals(dbPassword)) {
            throw new IllegalStateException("Production requires a non-default DATABASE_PASSWORD");
        }
    }
}
