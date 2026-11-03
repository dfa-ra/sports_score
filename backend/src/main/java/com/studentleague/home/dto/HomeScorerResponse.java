package com.studentleague.home.dto;

import java.util.UUID;

public record HomeScorerResponse(
        UUID playerId,
        String displayName,
        long goals,
        long assists,
        long appearances,
        UUID teamId,
        String teamName,
        String avatarUrl,
        String teamLogoUrl
) {
}
