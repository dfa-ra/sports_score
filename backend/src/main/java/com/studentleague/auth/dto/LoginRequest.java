package com.studentleague.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Укажите почту")
        @Email(message = "Укажите корректную почту")
        String email,
        @NotBlank(message = "Укажите пароль") String password
) {
}
