package com.studentleague.tournaments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OpenPlayoffRequest(
        @NotNull @Size(min = 1, max = 8) @Valid List<PlayoffPairRequest> pairs
) {
}
