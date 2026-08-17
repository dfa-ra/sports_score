package com.studentleague.matches.scoring;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FootballScorePolicy implements ScorePolicy {

    @Override
    public String sportCode() {
        return "FOOTBALL";
    }

    @Override
    public ScoreSnapshot calculate(UUID homeTeamId, UUID awayTeamId, List<MatchEvent> activeEvents) {
        int home = 0;
        int away = 0;
        for (MatchEvent event : activeEvents) {
            if (event.getEventType() != MatchEventType.GOAL || event.getTeamId() == null) {
                continue;
            }
            boolean ownGoal = event.getMetadata() != null
                    && Boolean.TRUE.equals(event.getMetadata().get("ownGoal"));
            if (event.getTeamId().equals(homeTeamId)) {
                if (ownGoal) {
                    away++;
                } else {
                    home++;
                }
            } else if (event.getTeamId().equals(awayTeamId)) {
                if (ownGoal) {
                    home++;
                } else {
                    away++;
                }
            }
        }
        return new ScoreSnapshot(home, away);
    }
}
