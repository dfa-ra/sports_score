package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayingTimeTest {

    @Test
    void twoByFifteenCapsABloatedClockAndCountsSubstitutions() {
        UUID starter = UUID.randomUUID();
        UUID bench = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-02T12:00:00Z");

        Match finished = match(MatchStatus.FINISHED, 2, 64 * 60);
        assertEquals(30, PlayingTime.minutesPlayed(finished, true, starter, List.of(), now));

        Match paused = match(MatchStatus.PAUSED, 1, 5 * 60);
        assertEquals(5, PlayingTime.minutesPlayed(paused, true, starter, List.of(), now.plusSeconds(3600)));

        Match full = match(MatchStatus.FINISHED, 2, 15 * 60);
        MatchEvent off = sub(starter, bench, 8 * 60, 1);
        assertEquals(8, PlayingTime.minutesPlayed(full, true, starter, List.of(off), now));
        assertEquals(22, PlayingTime.minutesPlayed(full, false, bench, List.of(off), now));
        assertNull(PlayingTime.minutesPlayed(full, false, UUID.randomUUID(), List.of(), now));
    }

    private static Match match(MatchStatus status, int period, int elapsedInPeriod) {
        Match match = new Match();
        match.setStatus(status);
        match.setPeriod(period);
        match.setPeriodCount(2);
        match.setPeriodLengthSeconds(15 * 60);
        match.setGameTimeSeconds(elapsedInPeriod);
        return match;
    }

    private static MatchEvent sub(UUID out, UUID in, int gameTime, int period) {
        MatchEvent event = new MatchEvent();
        event.setEventType(MatchEventType.SUBSTITUTION);
        event.setPlayerId(out);
        event.setSecondaryPlayerId(in);
        event.setGameTime(gameTime);
        event.setPeriod(period);
        event.setTimestamp(Instant.parse("2026-11-02T12:08:00Z"));
        event.setVoided(false);
        return event;
    }
}
