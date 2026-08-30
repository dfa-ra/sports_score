package com.studentleague.tournaments.dto;

import java.util.List;
import java.util.UUID;

public record CalendarImportResponse(
        int created,
        int skipped,
        List<UUID> matchIds,
        List<String> warnings
) {
}
