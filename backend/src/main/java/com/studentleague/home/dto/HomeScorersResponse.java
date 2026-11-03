package com.studentleague.home.dto;

import java.util.List;
import java.util.UUID;

public record HomeScorersResponse(
        UUID tournamentId,
        String tournamentName,
        String shortName,
        List<HomeScorerResponse> scorers
) {
}
