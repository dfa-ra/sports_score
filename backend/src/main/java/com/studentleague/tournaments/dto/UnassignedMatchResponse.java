package com.studentleague.tournaments.dto;

import java.util.UUID;

public record UnassignedMatchResponse(
        UUID id,
        UUID homeTeamId,
        String homeTeamName,
        UUID awayTeamId,
        String awayTeamName,
        int homeScore,
        int awayScore,
        String status
) {
}
