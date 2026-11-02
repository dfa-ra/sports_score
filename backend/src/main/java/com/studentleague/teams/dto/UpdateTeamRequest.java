package com.studentleague.teams.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTeamRequest(
        @Size(max = 150, message = "Название не длиннее 150 символов") String name,
        @Size(max = 32) String shortName,
        String logoUrl,
        UUID captainPlayerId,
        LocalDate foundedOn
) {
}
