package com.studentleague.tournaments.dto;

import java.util.List;

public record PlayoffViewResponse(
        String competitionStage,
        List<Integer> advancingPlaces,
        List<QualifiedTeamResponse> qualified,
        List<PlayoffRoundResponse> rounds,
        List<UnassignedMatchResponse> unassignedMatches
) {
}
