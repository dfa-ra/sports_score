package com.studentleague.home.dto;

import java.util.UUID;

public record HomeLeaderResponse(
        UUID teamId,
        String teamName,
        String shortName,
        String logoUrl,
        int points,
        int played,
        String groupName
) {
}
