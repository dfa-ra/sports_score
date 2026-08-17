package com.studentleague.matches.live;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchEventResponse;

import java.util.UUID;

public record LiveMatchUpdate(
        String type,
        UUID matchId,
        MatchStatus status,
        int homeScore,
        int awayScore,
        Integer gameTimeSeconds,
        Integer period,
        MatchEventResponse lastEvent
) {
}
