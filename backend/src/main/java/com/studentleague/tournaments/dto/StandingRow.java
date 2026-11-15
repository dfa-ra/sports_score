package com.studentleague.tournaments.dto;

import java.util.List;
import java.util.UUID;

public record StandingRow(
        UUID teamId,
        String teamName,
        int played,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int points,
        List<StandingFormMark> form
) {
}
