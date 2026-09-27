package com.studentleague.tournaments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TournamentTableWriteRequest(
        @NotBlank @Size(max = 200) String name,
        List<UUID> teamIds
) {
}
