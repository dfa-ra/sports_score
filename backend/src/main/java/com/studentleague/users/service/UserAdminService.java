package com.studentleague.users.service;

import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.users.domain.Role;
import com.studentleague.users.dto.RequestRoleRequest;
import com.studentleague.users.dto.RoleAssignmentResponse;
import com.studentleague.users.dto.UpdateUserRequest;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final EnumSet<Role> ASSIGNABLE = EnumSet.of(
            Role.FAN, Role.PLAYER, Role.CAPTAIN, Role.REFEREE
    );

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final AppProperties appProperties;

    public UserAdminService(
            UserRepository userRepository,
            RoleService roleService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(roleService::toUserResponse);
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> pendingRoles() {
        return roleService.pendingRequests();
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
            if (user.getRole() == Role.ADMIN && isBootstrapAdmin(user.getEmail())) {
                throw ApiException.forbidden("Нельзя изменить роль основного администратора");
            }
            roleService.grantApproved(user, request.role(), user.getPhotoUrl());
        }
        if (request.enabled() != null) {
            if (Boolean.FALSE.equals(request.enabled()) && isBootstrapAdmin(user.getEmail())) {
                throw ApiException.forbidden("Нельзя отключить основного администратора");
            }
            user.setEnabled(request.enabled());
            userRepository.save(user);
        }
        return roleService.toUserResponse(user);
    }

    @Transactional
    public UserResponse addOrChangeRole(UUID userId, RequestRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (request.role() == Role.ADMIN) {
            throw ApiException.badRequest("Роль ADMIN задаётся только через ADMIN_EMAIL в .env");
        }
        roleService.grantApproved(user, request.role(), request.photoUrl() != null ? request.photoUrl() : user.getPhotoUrl());
        return roleService.toUserResponse(user);
    }

    @Transactional
    public UserResponse approveRole(UUID userId, Role role) {
        roleService.approve(userId, role);
        return roleService.toUserResponse(userRepository.findById(userId).orElseThrow());
    }

    @Transactional
    public UserResponse rejectRole(UUID userId, Role role, String note) {
        roleService.reject(userId, role, note);
        return roleService.toUserResponse(userRepository.findById(userId).orElseThrow());
    }

    private boolean isBootstrapAdmin(String email) {
        AppProperties.Admin admin = appProperties.admin();
        return admin != null && admin.email() != null
                && email != null
                && email.equalsIgnoreCase(admin.email().trim());
    }
}
