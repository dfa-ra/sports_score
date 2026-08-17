package com.studentleague.tournaments.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterTeamRequest(@NotNull UUID teamId) {
}
