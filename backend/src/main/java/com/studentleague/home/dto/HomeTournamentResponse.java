package com.studentleague.home.dto;

import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record HomeTournamentResponse(
        UUID id,
        String name,
        String shortName,
        TournamentStatus status,
        Integer seasonYear,
        String seasonLabel,
        LocalDate startDate,
        LocalDate endDate,
        String format,
        String formatTitle,
        int teamsCount,
        CompetitionStage competitionStage,
        String stageLabel,
        Integer currentRound,
        Integer roundsTotal,
        Double progress,
        HomeLeaderResponse leader,
        String playoffRoundTitle,
        Integer playoffPlayedPairs,
        Integer playoffTotalPairs,
        LocalDate registrationClosesOn,
        HomeMatchResponse lastMatch,
        HomeMatchResponse nextMatch
) {
}
