package com.studentleague.tournaments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceTournamentTablesRequest(
        @NotNull @Size(max = 16) List<@Valid TournamentTableWriteRequest> tables
) {
}
