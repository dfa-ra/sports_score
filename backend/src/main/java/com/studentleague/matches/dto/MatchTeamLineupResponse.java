package com.studentleague.matches.dto;

import java.util.List;
import java.util.UUID;

public record MatchTeamLineupResponse(
        UUID teamId,
        String teamName,
        UUID captainId,
        boolean confirmed,
        List<MatchLineupPlayerResponse> starters,
        List<MatchLineupPlayerResponse> bench
) {
}
