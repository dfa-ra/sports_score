package com.studentleague.matches.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminUpdateMatchEventRequest(
        @NotNull(message = "Укажите минуту") @Min(value = 0, message = "Минута не может быть отрицательной") Integer minute,
        UUID playerId,
        UUID secondaryPlayerId,
        Boolean updatePlayers,
        Boolean ownGoal
) {
}
