package com.studentleague.matches.service;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.players.entity.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchListFieldsTest {

    @Test
    void minuteUsesRunningClockAndStaysEmptyWithoutOne() {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");

        Match live = new Match();
        live.setStatus(MatchStatus.LIVE);
        live.setPeriod(1);
        live.setGameTimeSeconds(30);
        live.setPeriodLengthSeconds(20 * 60);
        live.setClockRunningSince(now.minusSeconds(95));
        assertThat(MatchListFields.minute(live, now)).isEqualTo(2);

        Match paused = new Match();
        paused.setStatus(MatchStatus.PAUSED);
        paused.setPeriod(2);
        paused.setGameTimeSeconds(60);
        paused.setPeriodLengthSeconds(20 * 60);
        assertThat(MatchListFields.minute(paused, now)).isEqualTo(1);

        Match scheduled = new Match();
        scheduled.setStatus(MatchStatus.SCHEDULED);
        scheduled.setGameTimeSeconds(600);
        assertThat(MatchListFields.minute(scheduled, now)).isNull();

        Match noClock = new Match();
        noClock.setStatus(MatchStatus.LIVE);
        assertThat(MatchListFields.minute(noClock, now)).isNull();
    }

    @Test
    void lastGoalScorerIsTheNewestNamedGoal() {
        UUID matchId = UUID.randomUUID();
        UUID newestPlayer = UUID.randomUUID();
        UUID olderPlayer = UUID.randomUUID();

        PlayerProfile newest = new PlayerProfile();
        newest.setId(newestPlayer);
        newest.setFirstName("Иван");
        newest.setLastName("Иванов");
        newest.setDisplayName("Vanya");

        PlayerProfile older = new PlayerProfile();
        older.setId(olderPlayer);
        older.setFirstName("Пётр");
        older.setLastName("Сидоров");

        MatchEvent latest = goal(matchId, newestPlayer, Instant.parse("2026-09-30T12:10:00Z"));
        MatchEvent previous = goal(matchId, olderPlayer, Instant.parse("2026-09-30T12:04:00Z"));

        Map<UUID, String> names = MatchListFields.lastGoalScorers(
                List.of(latest, previous),
                Map.of(newestPlayer, newest, olderPlayer, older)
        );

        assertThat(names).containsEntry(matchId, "Иванов Иван");
    }

    private static MatchEvent goal(UUID matchId, UUID playerId, Instant timestamp) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setPlayerId(playerId);
        event.setEventType(MatchEventType.GOAL);
        event.setTimestamp(timestamp);
        event.setVoided(false);
        return event;
    }
}
