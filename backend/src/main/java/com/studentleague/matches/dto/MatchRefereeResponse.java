package com.studentleague.matches.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public record MatchRefereeResponse(
        UUID id,
        UUID matchId,
        UUID refereeId,
        Instant assignedAt,
        String firstName,
        String lastName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String email
) {
}
