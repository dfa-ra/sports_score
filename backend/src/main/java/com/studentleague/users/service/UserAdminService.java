package com.studentleague.users.service;

import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.users.domain.Role;
import com.studentleague.users.dto.UpdateUserRequest;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final EnumSet<Role> ASSIGNABLE = EnumSet.of(
            Role.FAN, Role.PLAYER, Role.CAPTAIN, Role.REFEREE
    );

    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public UserAdminService(UserRepository userRepository, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (request.role() != null) {
            if (request.role() == Role.ADMIN) {
                throw ApiException.badRequest("Роль ADMIN задаётся только через ADMIN_EMAIL в .env");
            }
            if (!ASSIGNABLE.contains(request.role())) {
                throw ApiException.badRequest("Недопустимая роль");
            }
            // Нельзя снять ADMIN с bootstrap-аккаунта через API
            if (user.getRole() == Role.ADMIN && isBootstrapAdmin(user.getEmail())) {
                throw ApiException.forbidden("Нельзя изменить роль основного администратора");
            }
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            if (Boolean.FALSE.equals(request.enabled()) && isBootstrapAdmin(user.getEmail())) {
                throw ApiException.forbidden("Нельзя отключить основного администратора");
            }
            user.setEnabled(request.enabled());
        }
        return toResponse(userRepository.save(user));
    }

    private boolean isBootstrapAdmin(String email) {
        AppProperties.Admin admin = appProperties.admin();
        return admin != null && admin.email() != null
                && email != null
                && email.equalsIgnoreCase(admin.email().trim());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled());
    }
}
