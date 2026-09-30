package com.studentleague.matches.dto;

import jakarta.validation.constraints.Size;

public record UpdateMatchRequest(
        @Size(max = 120) String venue
) {
}
