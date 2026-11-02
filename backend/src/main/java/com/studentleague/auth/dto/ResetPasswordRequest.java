package com.studentleague.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Ссылка недействительна или устарела")
        @Size(max = 512, message = "Ссылка недействительна или устарела")
        String token,
        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
        String password
) {
}
