package com.studentleague.matches.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminUpdateMatchEventRequest(
        @NotNull @Min(0) @Max(200) Integer minute
) {
}
