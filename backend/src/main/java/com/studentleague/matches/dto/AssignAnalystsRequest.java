package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AnalystAssignmentMode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignAnalystsRequest(
        @NotNull AnalystAssignmentMode mode,
        UUID userId,
        UUID homeUserId,
        UUID awayUserId
) {
}
