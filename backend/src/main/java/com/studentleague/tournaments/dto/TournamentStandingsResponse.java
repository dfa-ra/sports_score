package com.studentleague.tournaments.dto;

import java.util.List;

public record TournamentStandingsResponse(
        List<StandingTableResponse> tables
) {
}
