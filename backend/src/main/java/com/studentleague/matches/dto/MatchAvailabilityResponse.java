package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchAvailabilityResponse(
        UUID id,
        UUID matchId,
        UUID playerId,
        String displayName,
        AvailabilityStatus status,
        Instant updatedAt,
        String firstName,
        String lastName
) {
}
