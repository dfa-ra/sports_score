package com.studentleague.tournaments.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Header for a tournament page: format, size, stage, and the counts the tabs show.
 * {@code stage} is the competition stage ({@code GROUPS} or {@code PLAYOFF}).
 * {@code hasPlayoff} is true when the format includes a bracket, the stage is already
 * playoff, or slots exist. {@code playoffLive} is true when a bracket match is live or paused.
 */
public record TournamentOverviewResponse(
        UUID id,
        String name,
        String status,
        String format,
        Integer seasonYear,
        LocalDate startDate,
        LocalDate endDate,
        int teamsCount,
        int matchesCount,
        String stage,
        boolean hasPlayoff,
        boolean playoffLive
) {
}
