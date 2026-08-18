package com.studentleague.matches.dto;

import com.studentleague.matches.domain.MatchEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MatchEventResponse(
        UUID id,
        UUID matchId,
        MatchEventType eventType,
        Instant timestamp,
        Integer gameTime,
        Integer period,
        UUID teamId,
        UUID playerId,
        String playerName,
        Integer playerJersey,
        UUID secondaryPlayerId,
        String secondaryPlayerName,
        Integer secondaryPlayerJersey,
        Map<String, Object> metadata,
        boolean voided,
        Instant voidedAt,
        Instant createdAt
) {
}
