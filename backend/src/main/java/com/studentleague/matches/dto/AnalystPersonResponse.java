package com.studentleague.matches.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

public record AnalystPersonResponse(
        UUID id,
        String firstName,
        String lastName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String email
) {
}
