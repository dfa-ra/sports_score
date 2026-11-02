package com.studentleague.tournaments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record PlayoffPairRequest(
        @NotNull UUID homeTeamId,
        @NotNull UUID awayTeamId,
        @NotNull Instant scheduledAt,
        @Size(max = 120) String venue
) {
}
