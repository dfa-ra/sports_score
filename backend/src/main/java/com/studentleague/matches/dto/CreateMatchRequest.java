package com.studentleague.matches.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateMatchRequest(
        @NotNull UUID tournamentId,
        @NotNull UUID homeTeamId,
        @NotNull UUID awayTeamId,
        @NotNull Instant scheduledAt,
        @Min(1) @Max(8) Integer periodCount,
        @Min(1) @Max(90) Integer periodLengthMinutes
) {
}
