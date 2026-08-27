package com.studentleague.users.dto;

import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentResponse(
        UUID id,
        UUID userId,
        Role role,
        RoleStatus status,
        String photoUrl,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewNote
) {
}
