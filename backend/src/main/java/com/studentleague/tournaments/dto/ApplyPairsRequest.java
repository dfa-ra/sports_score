package com.studentleague.tournaments.dto;

import com.studentleague.tournaments.domain.QualifyStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ApplyPairsRequest(
        boolean confirm,
        @Valid List<PairEdit> pairs
) {
    public record PairEdit(
            @NotNull QualifyStage stage,
            @Min(0) int pairIndex,
            UUID homeGroupId,
            @Min(1) @Max(64) Integer homePlace,
            UUID awayGroupId,
            @Min(1) @Max(64) Integer awayPlace
    ) {
    }
}
