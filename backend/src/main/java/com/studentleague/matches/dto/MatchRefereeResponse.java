package com.studentleague.matches.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchRefereeResponse(
        UUID id,
        UUID matchId,
        UUID refereeId,
        Instant assignedAt
) {
}
