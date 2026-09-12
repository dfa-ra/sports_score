package com.studentleague.auth.dto;

import com.studentleague.users.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "FAN|PLAYER|CAPTAIN|REFEREE") String role,
        @Pattern(regexp = "FAN|PLAYER|CAPTAIN|REFEREE") String accountType,
        List<@Pattern(regexp = "FAN|PLAYER|CAPTAIN|REFEREE") String> roles,
        @Size(max = 1024) String photoUrl
) {
    private static final Set<String> PHOTO_ROLES = Set.of("PLAYER", "CAPTAIN", "REFEREE");

    public String resolvedRole() {
        List<Role> resolved = resolvedRoles();
        return resolved.isEmpty() ? null : resolved.getFirst().name();
    }

    public List<Role> resolvedRoles() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (roles != null) {
            for (String item : roles) {
                if (item != null && !item.isBlank()) {
                    names.add(item.trim());
                }
            }
        }
        if (role != null && !role.isBlank()) {
            names.add(role.trim());
        }
        if (names.isEmpty() && accountType != null && !accountType.isBlank()) {
            names.add(accountType.trim());
        }
        List<Role> resolved = new ArrayList<>();
        for (String name : names) {
            resolved.add(Role.valueOf(name));
        }
        return resolved;
    }

    @AssertTrue(message = "Укажите хотя бы одну роль")
    public boolean isRolePresent() {
        return !resolvedRoles().isEmpty();
    }

    @AssertTrue(message = "Для игрока, капитана и судьи нужна фотография")
    public boolean isPhotoValid() {
        boolean needsPhoto = resolvedRoles().stream().anyMatch(item -> PHOTO_ROLES.contains(item.name()));
        if (!needsPhoto) {
            return true;
        }
        return photoUrl != null && !photoUrl.isBlank();
    }
}
