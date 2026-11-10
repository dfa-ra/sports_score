package com.studentleague.tournaments.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayoffQualifyResponse(
        List<GroupBlock> groups,
        List<RuleRow> rules,
        Derived derived,
        boolean locked,
        boolean bracketBuilt
) {
    public record GroupBlock(
            UUID id,
            String name,
            int teamCount,
            boolean finished,
            List<PlaceRow> places
    ) {
    }

    public record PlaceRow(
            int place,
            UUID teamId,
            String teamName,
            String stage
    ) {
    }

    public record RuleRow(
            UUID groupId,
            int placeFrom,
            int placeTo,
            String stage
    ) {
    }

    public record Derived(Map<String, StageMath> perStage) {
    }

    public record StageMath(int direct, int fromPrev, int total, boolean powerOfTwo) {
    }
}
