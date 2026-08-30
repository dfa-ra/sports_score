package com.studentleague.auth.dto;

import com.studentleague.users.domain.Role;
import com.studentleague.users.dto.RoleAssignmentResponse;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        boolean enabled,
        String firstName,
        String lastName,
        String photoUrl,
        List<RoleAssignmentResponse> roles
) {
}
