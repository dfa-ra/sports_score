package com.studentleague.matches.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SetMatchLineupRequest(
        @NotNull UUID teamId,
        @NotEmpty List<UUID> starterPlayerIds,
        List<UUID> benchPlayerIds
) {
}
