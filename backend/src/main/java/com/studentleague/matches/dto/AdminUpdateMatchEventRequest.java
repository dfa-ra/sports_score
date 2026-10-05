package com.studentleague.matches.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminUpdateMatchEventRequest(
        @NotNull @Min(0) @Max(200) Integer minute,
        UUID playerId,
        UUID secondaryPlayerId,
        Boolean updatePlayers
) {
}
