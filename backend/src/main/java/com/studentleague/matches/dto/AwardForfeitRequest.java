package com.studentleague.matches.dto;

import com.studentleague.matches.domain.ForfeitReason;
import com.studentleague.matches.domain.ForfeitSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AwardForfeitRequest(
        @NotNull ForfeitSide side,
        @NotNull ForfeitReason reason,
        @Size(max = 500) String note
) {
}
