package com.studentleague.config;

import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Создаёт (или обновляет) администратора из ADMIN_EMAIL / ADMIN_PASSWORD
 * и снимает ADMIN с остальных.
 * <p>
 * E2E_ADMIN_EMAIL сохраняет дополнительные адреса только вне production.
 * Оба стенда включают Spring-профиль {@code prod} ({@code deploy/.env.example}
 * и {@code deploy/.env.prod.example}). Production — это compose-проект с
 * {@code prod} в имени (как {@code stand.sh}) или публичный хост {@code itmoliga.ru}.
 * Локальный профиль {@code dev} и dev-стенд ({@code studentleague-dev}) переменную учитывают.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    static final String SINGLE_ADMIN_NOTE = "Единственный админ задаётся через .env";

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final Environment environment;

    public AdminBootstrapRunner(
            AppProperties appProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RoleService roleService,
            Environment environment
    ) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = appProperties.admin();
        if (admin == null
                || admin.email() == null || admin.email().isBlank()
                || admin.password() == null || admin.password().isBlank()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD не заданы — админ не создан");
            return;
        }

        String email = admin.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        boolean creating = user.getId() == null;
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName("Админ");
        }
        if (user.getLastName() == null || user.getLastName().isBlank()) {
            user.setLastName("Лиги");
        }
        userRepository.save(user);
        roleService.grantApproved(user, Role.ADMIN, null);
        roleService.grantApproved(user, Role.FAN, null);

        Set<String> kept = keptAdminEmails(email, admin.e2eEmail(), environment);
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .filter(u -> u.getEmail() == null || !kept.contains(normalize(u.getEmail())))
                .forEach(u -> {
                    roleService.reject(u.getId(), Role.ADMIN, SINGLE_ADMIN_NOTE);
                    log.info("Роль ADMIN снята с {}", u.getEmail());
                });

        log.info("Админ {} {}", email, creating ? "создан" : "обновлён");
    }

    /**
     * Адрес из ADMIN_EMAIL плюс адреса из E2E_ADMIN_EMAIL, если это не production.
     * Запятая в ADMIN_EMAIL не добавляет второго админа.
     */
    static Set<String> keptAdminEmails(String primaryEmail, String e2eEmail, Environment environment) {
        Set<String> kept = new HashSet<>();
        kept.add(primaryEmail);
        if (e2eEmail == null || e2eEmail.isBlank()) {
            return kept;
        }
        if (!honorE2eAdmin(environment)) {
            log.info("E2E_ADMIN_EMAIL задан и на production игнорируется");
            return kept;
        }
        for (String part : e2eEmail.split(",")) {
            String extra = normalize(part);
            if (!extra.isEmpty()) {
                kept.add(extra);
            }
        }
        return kept;
    }

    static boolean honorE2eAdmin(Environment environment) {
        String project = environment.getProperty("COMPOSE_PROJECT_NAME", "");
        String publicUrl = environment.getProperty("app.public-url", "");
        if (contains(project, "prod") || isProductionHost(publicUrl)) {
            return false;
        }
        if (!springProd(environment)) {
            return true;
        }
        // Spring-профиль prod на обоих стендах. Dev-стенд узнаём по compose-имени или публичному IP.
        return contains(project, "dev") || isDevStandHost(publicUrl);
    }

    private static boolean springProd(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String token) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(token);
    }

    static boolean isProductionHost(String publicUrl) {
        String host = hostOf(publicUrl);
        return "itmoliga.ru".equals(host) || host.endsWith(".itmoliga.ru");
    }

    static boolean isDevStandHost(String publicUrl) {
        return "144.31.153.52".equals(hostOf(publicUrl));
    }

    private static String hostOf(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "";
        }
        String trimmed = publicUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null && !trimmed.contains("://")) {
                host = URI.create("https://" + trimmed).getHost();
            }
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
