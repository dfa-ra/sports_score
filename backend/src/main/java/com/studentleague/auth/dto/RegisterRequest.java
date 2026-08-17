package com.studentleague.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Регистрация: только зритель (FAN) или игрок (PLAYER).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull @Pattern(regexp = "FAN|PLAYER") String accountType,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName
) {
    @AssertTrue(message = "Для игрока укажите имя и фамилию")
    public boolean isPlayerNamesValid() {
        if (!"PLAYER".equals(accountType)) {
            return true;
        }
        return firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank();
    }
}
