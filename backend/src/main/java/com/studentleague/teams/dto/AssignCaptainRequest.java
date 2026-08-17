package com.studentleague.teams.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCaptainRequest(
        @NotNull UUID playerId
) {
}
