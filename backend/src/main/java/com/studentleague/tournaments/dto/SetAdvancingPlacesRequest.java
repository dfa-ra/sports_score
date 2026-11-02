package com.studentleague.tournaments.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SetAdvancingPlacesRequest(
        @NotEmpty @Size(max = 16) List<@NotNull @Min(1) @Max(16) Integer> places
) {
}
