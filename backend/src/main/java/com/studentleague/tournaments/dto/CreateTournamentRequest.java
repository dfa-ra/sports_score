package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.TournamentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTournamentRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        String regulations,
        @NotNull UUID sportId,
        @NotNull Integer seasonYear,
        LocalDate startDate,
        LocalDate endDate,
        TournamentStatus status,
        @NotBlank @Size(max = 64) String format,
        Integer maxSquadSize
) {
}
