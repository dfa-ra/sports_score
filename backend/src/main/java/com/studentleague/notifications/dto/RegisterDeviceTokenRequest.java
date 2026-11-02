package com.studentleague.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDeviceTokenRequest(
        @NotBlank(message = "Укажите платформу")
        @Pattern(regexp = "ANDROID|IOS|WEB", message = "Платформа: ANDROID, IOS или WEB")
        String platform,
        @NotBlank String token
) {
}
