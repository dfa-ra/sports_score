package com.studentleague.config;

import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт (или обновляет) единственного администратора из ADMIN_EMAIL / ADMIN_PASSWORD.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    public AdminBootstrapRunner(
            AppProperties appProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RoleService roleService
    ) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
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

        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .filter(u -> !u.getEmail().equalsIgnoreCase(email))
                .forEach(u -> {
                    roleService.reject(u.getId(), Role.ADMIN, "Единственный админ задаётся через .env");
                    log.info("Роль ADMIN снята с {}", u.getEmail());
                });

        log.info("Админ {} {}", email, creating ? "создан" : "обновлён");
    }
}
