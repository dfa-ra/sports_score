package com.studentleague.matches.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studentleague.matches.domain.ForfeitReason;
import com.studentleague.matches.domain.ForfeitSide;
import com.studentleague.matches.domain.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID tournamentId,
        UUID sportId,
        UUID homeTeamId,
        UUID awayTeamId,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        MatchStatus status,
        int homeScore,
        int awayScore,
        Integer gameTimeSeconds,
        Integer period,
        int periodCount,
        int periodLengthSeconds,
        Instant clockRunningSince,
        String sportCode,
        String venue,
        String lastGoalScorer,
        Integer minute,
        PlayerOfTheMatchResponse playerOfTheMatch,
        PlayerOfTheMatchResponse homeGoalkeeper,
        PlayerOfTheMatchResponse awayGoalkeeper,
        UUID tableId,
        Integer bracketRound,
        Integer penaltyHome,
        Integer penaltyAway,
        String homeTeamName,
        String awayTeamName,
        String tournamentName,
        boolean thirdPlace,
        String stageLabel,
        ForfeitSide forfeit,
        ForfeitReason forfeitReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String forfeitReasonNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) ForfeitActorResponse forfeitSetBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant forfeitSetAt
) {
}
