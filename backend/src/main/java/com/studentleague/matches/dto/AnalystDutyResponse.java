package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AnalystCoverage;
import com.studentleague.matches.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalystDutyResponse(
        UUID id,
        UUID homeTeamId,
        UUID awayTeamId,
        int homeScore,
        int awayScore,
        MatchStatus status,
        Instant scheduledAt,
        Integer minute,
        AnalystCoverage coverage,
        boolean editable
) {
}
