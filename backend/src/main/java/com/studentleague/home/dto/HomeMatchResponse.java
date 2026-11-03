package com.studentleague.home.dto;

import com.studentleague.matches.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record HomeMatchResponse(
        UUID id,
        UUID tournamentId,
        String tournamentName,
        String tournamentShortName,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        MatchStatus status,
        UUID homeTeamId,
        String homeTeamName,
        String homeTeamShortName,
        String homeLogoUrl,
        UUID awayTeamId,
        String awayTeamName,
        String awayTeamShortName,
        String awayLogoUrl,
        int homeScore,
        int awayScore,
        Integer minute,
        Integer gameTimeSeconds,
        Integer period,
        int periodCount,
        int periodLengthSeconds,
        Instant clockRunningSince,
        Integer bracketRound,
        Integer penaltyHome,
        Integer penaltyAway,
        String stageLabel,
        Integer round
) {
}
