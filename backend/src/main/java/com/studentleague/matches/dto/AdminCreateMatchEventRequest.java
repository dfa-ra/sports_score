package com.studentleague.matches.dto;

import com.studentleague.matches.domain.MatchEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminCreateMatchEventRequest(
        @NotNull MatchEventType eventType,
        @NotNull UUID teamId,
        @NotNull UUID playerId,
        @NotNull @Min(0) @Max(200) Integer minute
) {
}
