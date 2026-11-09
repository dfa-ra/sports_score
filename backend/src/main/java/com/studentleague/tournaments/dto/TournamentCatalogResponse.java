package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the public tournament directory. The list endpoint stays unchanged.
 */
public record TournamentCatalogResponse(
        UUID id,
        String name,
        TournamentStatus status,
        String format,
        CompetitionStage competitionStage,
        LocalDate startDate,
        LocalDate endDate,
        Integer seasonYear,
        int teamsCount,
        String stage,
        Double progress,
        String roundTitle,
        String winnerName,
        UUID winnerTeamId,
        int liveCount
) {
}
