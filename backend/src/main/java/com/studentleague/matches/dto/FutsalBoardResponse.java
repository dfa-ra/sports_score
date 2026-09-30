package com.studentleague.matches.dto;

import java.util.List;
import java.util.UUID;

public record FutsalBoardResponse(
        String foulScope,
        Integer period,
        String scopeNote,
        List<TeamFutsalState> teams
) {
    public record TeamFutsalState(
            UUID teamId,
            int fouls,
            boolean tenMeters,
            boolean timeoutAvailable,
            boolean shortHanded,
            Integer shortHandedRemainingSeconds,
            Integer shortHandedEndsAtGameTime
    ) {
    }
}
