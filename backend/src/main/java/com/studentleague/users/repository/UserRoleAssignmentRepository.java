package com.studentleague.users.repository;

import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {
    List<UserRoleAssignment> findByUserId(UUID userId);

    List<UserRoleAssignment> findByUserIdIn(Iterable<UUID> userIds);

    Optional<UserRoleAssignment> findByUserIdAndRole(UUID userId, Role role);

    List<UserRoleAssignment> findByStatusOrderByRequestedAtAsc(RoleStatus status);

    boolean existsByUserIdAndRoleAndStatus(UUID userId, Role role, RoleStatus status);
}
