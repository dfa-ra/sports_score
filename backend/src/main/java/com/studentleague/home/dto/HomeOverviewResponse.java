package com.studentleague.home.dto;

import com.studentleague.statistics.dto.LeagueSummaryResponse;

import java.time.Instant;
import java.util.List;

public record HomeOverviewResponse(
        Instant generatedAt,
        HomeMatchResponse next,
        List<HomeMatchResponse> live,
        List<HomeMatchResponse> upcoming,
        List<HomeMatchResponse> recent,
        List<HomeTournamentResponse> tournaments,
        List<HomeScorersResponse> scorersByTournament,
        LeagueSummaryResponse summary
) {
}
