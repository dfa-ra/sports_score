package com.studentleague.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank(message = "Не удалось войти через Google")
        @Size(max = 8192, message = "Не удалось войти через Google")
        String idToken
) {
}
