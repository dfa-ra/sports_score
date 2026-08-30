package com.studentleague.users.dto;

import com.studentleague.users.domain.Role;

import java.util.List;

public record UpdateUserRequest(
        Role role,
        List<Role> roles,
        Boolean enabled
) {
}
