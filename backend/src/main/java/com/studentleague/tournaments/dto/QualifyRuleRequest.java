package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.QualifyStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QualifyRuleRequest(
        @NotNull UUID groupId,
        @NotNull @Min(1) @Max(64) Integer placeFrom,
        @NotNull @Min(1) @Max(64) Integer placeTo,
        @NotNull QualifyStage stage
) {
}
