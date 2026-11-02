package com.studentleague.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Укажите почту")
        @Email(message = "Укажите корректную почту")
        @Size(max = 320, message = "Почта не длиннее 320 символов")
        String email
) {
}
