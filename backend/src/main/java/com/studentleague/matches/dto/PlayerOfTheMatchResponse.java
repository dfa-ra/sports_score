package com.studentleague.matches.dto;

import java.util.UUID;

public record PlayerOfTheMatchResponse(
        UUID playerId,
        String firstName,
        String lastName
) {
}
