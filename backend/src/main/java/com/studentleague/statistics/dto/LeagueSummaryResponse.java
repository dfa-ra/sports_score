package com.studentleague.statistics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LeagueSummaryResponse(
        long matchesPlayed,
        long goals,
        BigDecimal goalsPerMatch,
        long teams,
        long players,
        Instant updatedAt
) {
}
