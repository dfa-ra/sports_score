package com.studentleague.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDeviceTokenRequest(
        @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform,
        @NotBlank String token
) {
}
