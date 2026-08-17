package com.studentleague.matches.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateMatchRequest(
        @NotNull UUID tournamentId,
        @NotNull UUID homeTeamId,
        @NotNull UUID awayTeamId,
        @NotNull Instant scheduledAt
) {
}
