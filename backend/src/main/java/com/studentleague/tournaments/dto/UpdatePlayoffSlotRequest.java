package com.studentleague.tournaments.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record UpdatePlayoffSlotRequest(
        Instant scheduledAt,
        @Size(max = 120) String venue,
        UUID homeTeamId,
        UUID awayTeamId
) {
}
