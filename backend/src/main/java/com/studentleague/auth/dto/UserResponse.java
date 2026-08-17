package com.studentleague.auth.dto;

import com.studentleague.users.domain.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        boolean enabled
) {
}
