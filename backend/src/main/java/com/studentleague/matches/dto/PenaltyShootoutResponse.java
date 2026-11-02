package com.studentleague.matches.dto;

import java.util.List;

public record PenaltyShootoutResponse(
        Integer penaltyHome,
        Integer penaltyAway,
        boolean decisive,
        List<PenaltyKickResponse> kicks
) {
}
