package com.studentleague.tournaments.dto;

import java.util.List;
import java.util.UUID;

public record StandingTableResponse(
        UUID id,
        String name,
        int sortOrder,
        List<StandingRow> rows
) {
}
