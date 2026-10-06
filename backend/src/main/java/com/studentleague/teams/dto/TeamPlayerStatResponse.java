package com.studentleague.teams.dto;

import java.util.UUID;

public record TeamPlayerStatResponse(
        UUID playerId,
        String firstName,
        String lastName,
        String photoUrl,
        long goals,
        long assists,
        long appearances,
        long yellowCards
) {
}
