package com.studentleague.matches.dto;

import com.studentleague.matches.domain.MatchEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminCreateMatchEventRequest(
        @NotNull(message = "Укажите тип события") MatchEventType eventType,
        @NotNull(message = "Укажите команду") UUID teamId,
        @NotNull(message = "Укажите игрока") UUID playerId,
        @NotNull(message = "Укажите минуту") @Min(value = 0, message = "Минута не может быть отрицательной") Integer minute,
        Boolean ownGoal
) {
}
