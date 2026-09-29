package com.studentleague.statistics.dto;

import java.util.List;

public record StatisticsBoardResponse(
        List<PlayerStatisticsResponse> scorers,
        List<PlayerStatisticsResponse> assists,
        List<PlayerStatisticsResponse> goalkeepers
) {
}
