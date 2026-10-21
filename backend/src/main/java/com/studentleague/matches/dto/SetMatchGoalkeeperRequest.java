package com.studentleague.matches.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code playerId} null clears the pick and the lineup goalkeeper is used again. */
public record SetMatchGoalkeeperRequest(
        @NotNull UUID teamId,
        UUID playerId
) {
}
