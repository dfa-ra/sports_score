package com.studentleague.users.dto;

import com.studentleague.users.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RequestRoleRequest(
        @NotNull Role role,
        String photoUrl,
        String reviewNote
) {
}
