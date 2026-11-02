package com.studentleague.tournaments.dto;

import java.util.UUID;

public record QualifiedTeamResponse(
        UUID teamId,
        String teamName,
        UUID tableId,
        String groupName,
        int place
) {
}
