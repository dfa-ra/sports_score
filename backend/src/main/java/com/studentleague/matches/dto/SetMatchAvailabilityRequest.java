package com.studentleague.matches.dto;

import com.studentleague.matches.domain.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetMatchAvailabilityRequest(
        @NotNull AvailabilityStatus status,
        UUID playerId
) {
}
