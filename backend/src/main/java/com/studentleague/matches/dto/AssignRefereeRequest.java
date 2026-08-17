package com.studentleague.matches.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRefereeRequest(@NotNull UUID refereeId) {
}
