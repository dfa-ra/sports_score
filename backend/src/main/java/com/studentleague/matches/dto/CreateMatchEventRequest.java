package com.studentleague.matches.dto;

import com.studentleague.matches.domain.MatchEventType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreateMatchEventRequest(
        @NotNull MatchEventType eventType,
        Integer gameTime,
        UUID teamId,
        UUID playerId,
        UUID secondaryPlayerId,
        Map<String, Object> metadata
) {
}
