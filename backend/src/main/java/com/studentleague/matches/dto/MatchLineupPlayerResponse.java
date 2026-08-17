package com.studentleague.matches.dto;

import java.util.UUID;

public record MatchLineupPlayerResponse(
        UUID playerId,
        String name,
        Integer jerseyNumber,
        String position,
        boolean starter,
        int sortOrder
) {
}
