package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AnalystAssignmentMode;

import java.util.UUID;

public record AnalystAssignmentResponse(
        UUID matchId,
        AnalystAssignmentMode mode,
        AnalystPersonResponse analyst,
        AnalystPersonResponse homeAnalyst,
        AnalystPersonResponse awayAnalyst
) {
}
