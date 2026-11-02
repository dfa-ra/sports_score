package com.studentleague.matches.dto;

import java.util.UUID;

public record PenaltyKickResponse(
        UUID id,
        UUID teamId,
        int order,
        int round,
        boolean scored
) {
}
