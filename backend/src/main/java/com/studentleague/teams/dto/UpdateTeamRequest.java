package com.studentleague.teams.dto;

import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(
        @Size(max = 150) String name,
        @Size(max = 32) String shortName,
        String logoUrl
) {
}
