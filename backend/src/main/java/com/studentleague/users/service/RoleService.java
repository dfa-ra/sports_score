package com.studentleague.users.service;

import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.dto.RoleAssignmentResponse;
import com.studentleague.users.entity.User;
import com.studentleague.users.entity.UserRoleAssignment;
import com.studentleague.users.repository.UserRoleAssignmentRepository;
import com.studentleague.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleService {

    public static final Set<Role> PHOTO_REQUIRED = EnumSet.of(Role.PLAYER, Role.CAPTAIN, Role.REFEREE);
    public static final Set<Role> SELF_REQUESTABLE = EnumSet.of(Role.FAN, Role.PLAYER, Role.CAPTAIN, Role.REFEREE);

    private final UserRoleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public RoleService(
            UserRoleAssignmentRepository assignmentRepository,
            UserRepository userRepository,
            AppProperties appProperties
    ) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    public boolean autoApprove() {
        return appProperties.auth() != null && appProperties.auth().autoApproveRoles();
    }

    public boolean hasApproved(UUID userId, Role role) {
        return assignmentRepository.existsByUserIdAndRoleAndStatus(userId, role, RoleStatus.APPROVED)
                || userRepository.findById(userId).map(user -> user.getRole() == role).orElse(false);
    }

    public List<UserRoleAssignment> assignmentsOf(UUID userId) {
        return assignmentRepository.findByUserId(userId);
    }

    @Transactional
    public UserRoleAssignment requestRole(User user, Role role, String photoUrl) {
        if (role == Role.ADMIN) {
            throw ApiException.badRequest("Роль ADMIN задаётся только через ADMIN_EMAIL");
        }
        if (!SELF_REQUESTABLE.contains(role)) {
            throw ApiException.badRequest("Эту роль нельзя запросить");
        }
        if (PHOTO_REQUIRED.contains(role) && (photoUrl == null || photoUrl.isBlank())) {
            throw ApiException.badRequest("Для роли " + role.name() + " нужна фотография");
        }

        RoleStatus status = (role == Role.FAN || autoApprove()) ? RoleStatus.APPROVED : RoleStatus.PENDING;
        UserRoleAssignment assignment = assignmentRepository.findByUserIdAndRole(user.getId(), role)
                .orElseGet(UserRoleAssignment::new);
        assignment.setUserId(user.getId());
        assignment.setRole(role);
        assignment.setStatus(status);
        if (photoUrl != null && !photoUrl.isBlank()) {
            assignment.setPhotoUrl(photoUrl.trim());
        }
        assignment.setRequestedAt(Instant.now());
        if (status == RoleStatus.APPROVED) {
            assignment.setReviewedAt(Instant.now());
            assignment.setReviewNote(null);
        } else {
            assignment.setReviewedAt(null);
            assignment.setReviewNote(null);
        }
        assignmentRepository.save(assignment);
        syncPrimaryRole(user);
        return assignment;
    }

    @Transactional
    public UserRoleAssignment grantApproved(User user, Role role, String photoUrl) {
        UserRoleAssignment assignment = assignmentRepository.findByUserIdAndRole(user.getId(), role)
                .orElseGet(UserRoleAssignment::new);
        assignment.setUserId(user.getId());
        assignment.setRole(role);
        assignment.setStatus(RoleStatus.APPROVED);
        if (photoUrl != null && !photoUrl.isBlank()) {
            assignment.setPhotoUrl(photoUrl.trim());
        }
        if (assignment.getRequestedAt() == null) {
            assignment.setRequestedAt(Instant.now());
        }
        assignment.setReviewedAt(Instant.now());
        assignment.setReviewNote(null);
        assignmentRepository.save(assignment);
        syncPrimaryRole(user);
        return assignment;
    }

    @Transactional
    public UserRoleAssignment approve(UUID userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        UserRoleAssignment assignment = assignmentRepository.findByUserIdAndRole(userId, role)
                .orElseThrow(() -> ApiException.notFound("Заявка на роль не найдена"));
        assignment.setStatus(RoleStatus.APPROVED);
        assignment.setReviewedAt(Instant.now());
        assignmentRepository.save(assignment);
        syncPrimaryRole(user);
        return assignment;
    }

    @Transactional
    public UserRoleAssignment reject(UUID userId, Role role, String note) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        UserRoleAssignment assignment = assignmentRepository.findByUserIdAndRole(userId, role)
                .orElseThrow(() -> ApiException.notFound("Заявка на роль не найдена"));
        if (role == Role.ADMIN && isBootstrapAdmin(user.getEmail())) {
            throw ApiException.forbidden("Нельзя снять роль основного администратора");
        }
        assignment.setStatus(RoleStatus.REJECTED);
        assignment.setReviewedAt(Instant.now());
        assignment.setReviewNote(note);
        assignmentRepository.save(assignment);
        syncPrimaryRole(user);
        return assignment;
    }

    @Transactional
    public void syncPrimaryRole(User user) {
        Role primary = assignmentRepository.findByUserId(user.getId()).stream()
                .filter(a -> a.getStatus() == RoleStatus.APPROVED)
                .map(UserRoleAssignment::getRole)
                .max(Comparator.comparingInt(RoleService::rank))
                .orElse(Role.FAN);
        user.setRole(primary);
        userRepository.save(user);
    }

    public UserResponse toUserResponse(User user) {
        List<RoleAssignmentResponse> roles = assignmentRepository.findByUserId(user.getId()).stream()
                .map(RoleService::toAssignmentResponse)
                .toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhotoUrl(),
                roles
        );
    }

    public static RoleAssignmentResponse toAssignmentResponse(UserRoleAssignment assignment) {
        return new RoleAssignmentResponse(
                assignment.getId(),
                assignment.getUserId(),
                assignment.getRole(),
                assignment.getStatus(),
                assignment.getPhotoUrl(),
                assignment.getRequestedAt(),
                assignment.getReviewedAt(),
                assignment.getReviewNote()
        );
    }

    public List<RoleAssignmentResponse> pendingRequests() {
        return assignmentRepository.findByStatusOrderByRequestedAtAsc(RoleStatus.PENDING).stream()
                .map(RoleService::toAssignmentResponse)
                .toList();
    }

    public static int rank(Role role) {
        return switch (role) {
            case ADMIN -> 100;
            case REFEREE -> 80;
            case CAPTAIN -> 60;
            case PLAYER -> 40;
            case FAN -> 10;
        };
    }

    private boolean isBootstrapAdmin(String email) {
        AppProperties.Admin admin = appProperties.admin();
        return admin != null && admin.email() != null
                && email != null
                && email.equalsIgnoreCase(admin.email().trim());
    }
}
