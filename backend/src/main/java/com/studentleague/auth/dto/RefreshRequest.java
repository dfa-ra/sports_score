package com.studentleague.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Сессия недействительна. Войдите снова.") String refreshToken
) {
}
