package com.studentleague.matches.scoring;

import com.studentleague.matches.entity.MatchEvent;

import java.util.List;
import java.util.UUID;

public interface ScorePolicy {
    String sportCode();

    ScoreSnapshot calculate(UUID homeTeamId, UUID awayTeamId, List<MatchEvent> activeEvents);
}
