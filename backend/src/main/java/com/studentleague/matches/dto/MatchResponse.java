package com.studentleague.matches.dto;

import com.studentleague.matches.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID tournamentId,
        UUID sportId,
        UUID homeTeamId,
        UUID awayTeamId,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        MatchStatus status,
        int homeScore,
        int awayScore,
        Integer gameTimeSeconds,
        Integer period,
        int periodCount,
        int periodLengthSeconds,
        Instant clockRunningSince,
        String sportCode
) {
}
