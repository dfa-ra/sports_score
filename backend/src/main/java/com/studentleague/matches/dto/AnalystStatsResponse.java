package com.studentleague.matches.dto;

import com.studentleague.matches.domain.PossessionSide;

import java.util.UUID;

public record AnalystStatsResponse(
        UUID matchId,
        TeamAnalystStatsResponse home,
        TeamAnalystStatsResponse away,
        boolean possessionTracked,
        int homePossessionSeconds,
        int awayPossessionSeconds,
        PossessionSide possessionSide,
        Integer homePossessionPercent,
        Integer awayPossessionPercent
) {
}
