package com.studentleague.teams.dto;

import com.studentleague.teams.domain.TeamMemberStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        UUID teamId,
        UUID playerId,
        String playerFirstName,
        String playerLastName,
        String displayName,
        Integer jerseyNumber,
        String position,
        Instant joinedAt,
        TeamMemberStatus status
) {
}
