package com.studentleague.statistics.dto;

import java.util.UUID;

public record PlayerStatisticsResponse(
        UUID playerId,
        String displayName,
        long goals,
        long assists,
        long yellowCards,
        long redCards,
        long appearances,
        UUID teamId
) {
}
