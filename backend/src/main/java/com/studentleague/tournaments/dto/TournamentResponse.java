package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.TournamentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TournamentResponse(
        UUID id,
        String name,
        String description,
        String regulations,
        UUID sportId,
        Integer seasonYear,
        LocalDate startDate,
        LocalDate endDate,
        TournamentStatus status,
        String format,
        Integer maxSquadSize,
        Instant createdAt,
        Instant updatedAt
) {
}
