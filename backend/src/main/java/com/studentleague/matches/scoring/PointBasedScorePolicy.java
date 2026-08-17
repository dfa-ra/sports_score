package com.studentleague.matches.scoring;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Point-based scoring for basketball, volleyball, hockey, etc.
 * Uses POINT events; metadata.points defaults to 1.
 */
@Component
public class PointBasedScorePolicy implements ScorePolicy {

    private final String sportCode;

    public PointBasedScorePolicy() {
        this("DEFAULT");
    }

    public PointBasedScorePolicy(String sportCode) {
        this.sportCode = sportCode;
    }

    @Override
    public String sportCode() {
        return sportCode;
    }

    @Override
    public ScoreSnapshot calculate(UUID homeTeamId, UUID awayTeamId, List<MatchEvent> activeEvents) {
        int home = 0;
        int away = 0;
        for (MatchEvent event : activeEvents) {
            if (event.getTeamId() == null) {
                continue;
            }
            int points = pointsFor(event);
            if (points <= 0) {
                continue;
            }
            if (event.getTeamId().equals(homeTeamId)) {
                home += points;
            } else if (event.getTeamId().equals(awayTeamId)) {
                away += points;
            }
        }
        return new ScoreSnapshot(home, away);
    }

    private int pointsFor(MatchEvent event) {
        if (event.getEventType() == MatchEventType.GOAL) {
            return 1;
        }
        if (event.getEventType() == MatchEventType.POINT) {
            Map<String, Object> metadata = event.getMetadata();
            if (metadata != null && metadata.get("points") instanceof Number number) {
                return number.intValue();
            }
            return 1;
        }
        return 0;
    }
}
