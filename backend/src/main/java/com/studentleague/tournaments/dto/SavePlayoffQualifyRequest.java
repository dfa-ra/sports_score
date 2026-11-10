package com.studentleague.tournaments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SavePlayoffQualifyRequest(
        @NotNull @NotEmpty @Valid List<QualifyRuleRequest> rules
) {
}
