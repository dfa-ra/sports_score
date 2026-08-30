package com.studentleague.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "FAN|PLAYER|CAPTAIN|REFEREE") String role,
        @Pattern(regexp = "FAN|PLAYER|CAPTAIN|REFEREE") String accountType,
        @Size(max = 1024) String photoUrl
) {
    private static final Set<String> PHOTO_ROLES = Set.of("PLAYER", "CAPTAIN", "REFEREE");

    public String resolvedRole() {
        if (role != null && !role.isBlank()) {
            return role;
        }
        return accountType;
    }

    @AssertTrue(message = "Укажите роль")
    public boolean isRolePresent() {
        return resolvedRole() != null && !resolvedRole().isBlank();
    }

    @AssertTrue(message = "Для игрока, капитана и судьи нужна фотография")
    public boolean isPhotoValid() {
        String resolved = resolvedRole();
        if (resolved == null || !PHOTO_ROLES.contains(resolved)) {
            return true;
        }
        return photoUrl != null && !photoUrl.isBlank();
    }
}
