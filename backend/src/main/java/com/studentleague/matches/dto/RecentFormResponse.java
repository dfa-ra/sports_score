package com.studentleague.matches.dto;

import com.studentleague.matches.domain.ForfeitSide;
import com.studentleague.matches.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

/** One finished match in a team's recent form, with both crests in the same row. */
public record RecentFormResponse(
        UUID id,
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
        Instant scheduledAt,
        MatchStatus status,
        Instant startedAt,
        ForfeitSide forfeit
) {
}
