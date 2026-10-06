package com.studentleague.matches.dto;

import java.util.UUID;

public record AnalystPersonResponse(
        UUID id,
        String firstName,
        String lastName,
        String email
) {
}
