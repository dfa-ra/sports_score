package com.studentleague.matches.dto;

import com.studentleague.matches.domain.PossessionSide;
import jakarta.validation.constraints.NotNull;

public record SetPossessionRequest(
        @NotNull PossessionSide side
) {
}
