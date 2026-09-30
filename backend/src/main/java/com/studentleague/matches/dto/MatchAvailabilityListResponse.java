package com.studentleague.matches.dto;

import java.util.List;
import java.util.UUID;

public record MatchAvailabilityListResponse(
        UUID matchId,
        int goingCount,
        int notGoingCount,
        List<MatchAvailabilityResponse> going,
        List<MatchAvailabilityResponse> notGoing
) {
}
