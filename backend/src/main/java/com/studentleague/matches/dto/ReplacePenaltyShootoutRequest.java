package com.studentleague.matches.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplacePenaltyShootoutRequest(
        @NotNull @Valid List<PenaltyKickRequest> kicks
) {
}
