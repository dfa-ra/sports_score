package com.studentleague.statistics.dto;

import java.util.UUID;

public record TeamStatisticsResponse(
        UUID teamId,
        String teamName,
        long wins,
        long draws,
        long losses,
        long points,
        long goalsFor,
        long goalsAgainst
) {
}
