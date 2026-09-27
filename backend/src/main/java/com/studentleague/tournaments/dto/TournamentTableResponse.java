package com.studentleague.tournaments.dto;

import java.util.List;
import java.util.UUID;

public record TournamentTableResponse(
        UUID id,
        UUID tournamentId,
        String name,
        int sortOrder,
        List<UUID> teamIds
) {
}
