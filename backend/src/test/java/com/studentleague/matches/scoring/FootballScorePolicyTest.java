package com.studentleague.matches.scoring;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FootballScorePolicyTest {

    private final FootballScorePolicy policy = new FootballScorePolicy();

    @Test
    void countsGoalsAndOwnGoals() {
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();

        MatchEvent homeGoal = event(MatchEventType.GOAL, home, null);
        MatchEvent awayGoal = event(MatchEventType.GOAL, away, null);
        MatchEvent ownGoal = event(MatchEventType.GOAL, home, Map.of("ownGoal", true));

        ScoreSnapshot snapshot = policy.calculate(home, away, List.of(homeGoal, awayGoal, ownGoal));
        assertThat(snapshot.homeScore()).isEqualTo(1);
        assertThat(snapshot.awayScore()).isEqualTo(2);
    }

    private static MatchEvent event(MatchEventType type, UUID teamId, Map<String, Object> metadata) {
        MatchEvent event = new MatchEvent();
        event.setEventType(type);
        event.setTeamId(teamId);
        event.setMetadata(metadata);
        event.setVoided(false);
        return event;
    }
}
