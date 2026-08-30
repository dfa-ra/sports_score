package com.studentleague.teams.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String shortName,
        String logoUrl,
        UUID captainId,
        LocalDate foundedOn,
        boolean disbanded,
        Instant createdAt,
        Instant updatedAt
) {
}
