package com.studentleague.matches.dto;

public record TeamAnalystStatsResponse(
        int shots,
        int shotsOnTarget,
        int saves,
        int corners,
        int fouls,
        int freeKicks,
        int kickIns,
        int woodwork
) {
}
