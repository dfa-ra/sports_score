package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.TournamentStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTournamentRequest(
        @Size(max = 200) String name,
        String description,
        Integer seasonYear,
        LocalDate startDate,
        LocalDate endDate,
        TournamentStatus status,
        @Size(max = 64) String format
) {
}
