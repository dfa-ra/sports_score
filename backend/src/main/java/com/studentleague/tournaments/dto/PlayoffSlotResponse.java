package com.studentleague.tournaments.dto;

import java.time.Instant;
import java.util.UUID;

public record PlayoffSlotResponse(
        UUID id,
        int round,
        int slotIndex,
        String roundTitle,
        UUID matchId,
        UUID homeTeamId,
        String homeTeamName,
        String homeGroupName,
        Integer homePlace,
        UUID awayTeamId,
        String awayTeamName,
        String awayGroupName,
        Integer awayPlace,
        String homeWaitingLabel,
        String awayWaitingLabel,
        Instant scheduledAt,
        String venue,
        String matchStatus,
        Integer homeScore,
        Integer awayScore,
        Integer penaltyHome,
        Integer penaltyAway
) {
}
