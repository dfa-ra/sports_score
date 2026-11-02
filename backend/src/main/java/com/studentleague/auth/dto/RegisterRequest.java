package com.studentleague.auth.dto;

import com.studentleague.common.PersonNames;
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
        @NotBlank(message = "Укажите почту")
        @Email(message = "Укажите корректную почту")
        @Size(max = 320, message = "Почта не длиннее 320 символов")
        String email,
        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
        String password,
        @NotBlank(message = "Укажите имя")
        @Size(max = 100, message = "Имя не длиннее 100 символов")
        @Pattern(regexp = PersonNames.PLAIN, message = PersonNames.MESSAGE)
        String firstName,
        @NotBlank(message = "Укажите фамилию")
        @Size(max = 100, message = "Фамилия не длиннее 100 символов")
        @Pattern(regexp = PersonNames.PLAIN, message = PersonNames.MESSAGE)
        String lastName,
        @Pattern(regexp = RegistrationRoles.PATTERN, message = RegistrationRoles.MESSAGE) String role,
        @Pattern(regexp = RegistrationRoles.PATTERN, message = RegistrationRoles.MESSAGE) String accountType,
        List<@Pattern(regexp = RegistrationRoles.PATTERN, message = RegistrationRoles.MESSAGE) String> roles,
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
            try {
                resolved.add(Role.valueOf(name));
            } catch (IllegalArgumentException ex) {
                // The pattern constraint reports the bad token. Do not turn it into a 500.
            }
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
