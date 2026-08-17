package com.studentleague.players.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerCardResponse(
        UUID id,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        Integer jerseyNumber,
        String position,
        TeamSummary team,
        Map<String, Object> statistics,
        List<MatchHistoryItem> matchHistory
) {
    public record TeamSummary(UUID id, String name, String shortName, String logoUrl) {
    }

    public record MatchHistoryItem(
            UUID matchId,
            String opponentName,
            Integer homeScore,
            Integer awayScore,
            String status
    ) {
    }
}
