package com.studentleague.players.dto;

import java.time.Instant;
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
            Instant scheduledAt,
            String tournamentName,
            String homeTeamName,
            String awayTeamName,
            String opponentName,
            boolean home,
            Integer homeScore,
            Integer awayScore,
            String status,
            String outcome,
            int goals,
            int assists,
            int yellowCards,
            int redCards,
            Integer minutesPlayed
    ) {
    }
}
