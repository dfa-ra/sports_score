package com.studentleague.players.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PlayerProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String displayName,
        LocalDate dateOfBirth,
        String avatarUrl,
        Integer jerseyNumber,
        String position,
        String bio
) {
}
