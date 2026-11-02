package com.studentleague.tournaments.dto;

import java.util.List;

public record PlayoffRoundResponse(
        int round,
        String title,
        List<PlayoffSlotResponse> slots
) {
}
