package com.studentleague.players.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PlayerProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 150) String displayName,
        LocalDate dateOfBirth,
        String avatarUrl,
        Integer jerseyNumber,
        @Size(max = 64) String position,
        String bio
) {
}
