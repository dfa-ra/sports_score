package com.studentleague.teams.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String shortName,
        String logoUrl,
        UUID captainId,
        boolean disbanded,
        Instant createdAt,
        Instant updatedAt
) {
}
