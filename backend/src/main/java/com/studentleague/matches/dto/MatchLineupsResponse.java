package com.studentleague.matches.dto;

public record MatchLineupsResponse(
        MatchTeamLineupResponse home,
        MatchTeamLineupResponse away
) {
}
