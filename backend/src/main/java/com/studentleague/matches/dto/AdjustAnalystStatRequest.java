package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AnalystStat;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjustAnalystStatRequest(
        @NotNull UUID teamId,
        @NotNull AnalystStat stat
) {
}
