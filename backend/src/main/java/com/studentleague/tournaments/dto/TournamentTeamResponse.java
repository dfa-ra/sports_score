package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.TournamentTeamStatus;

import java.time.Instant;
import java.util.UUID;

public record TournamentTeamResponse(
        UUID id,
        UUID tournamentId,
        UUID teamId,
        String teamName,
        TournamentTeamStatus status,
        Instant registeredAt,
        Instant approvedAt
) {
}
