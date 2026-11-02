package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Minutes a player was on the pitch, taken from the match clock.
 * Pauses freeze that clock, so the seconds already played stay in the total
 * and are not replaced by wall-clock time. Substitutions cut the interval.
 * The result never exceeds the regulation length (2×15 is 30).
 */
public final class PlayingTime {

    private PlayingTime() {
    }

    public static Integer minutesPlayed(
            Match match,
            boolean starter,
            UUID playerId,
            List<MatchEvent> events,
            Instant now
    ) {
        if (match == null || playerId == null) {
            return null;
        }
        int length = MatchClock.periodLength(match);
        int periods = match.getPeriodCount() > 0 ? match.getPeriodCount() : MatchClock.DEFAULT_PERIOD_COUNT;
        int capSeconds = periods * length;
        int end = Math.min(capSeconds, Math.max(0, MatchClock.kickoffSeconds(match, now)));
        List<MatchEvent> subs = new ArrayList<>();
        if (events != null) {
            for (MatchEvent event : events) {
                if (event.getEventType() == MatchEventType.SUBSTITUTION
                        && !event.isVoided()
                        && (playerId.equals(event.getPlayerId()) || playerId.equals(event.getSecondaryPlayerId()))) {
                    subs.add(event);
                }
            }
        }
        subs.sort(Comparator
                .comparingInt((MatchEvent event) -> clockSecond(event, length))
                .thenComparing(event -> event.getTimestamp() == null ? Instant.EPOCH : event.getTimestamp()));
        if (!starter && subs.isEmpty()) {
            return null;
        }
        boolean onPitch = starter;
        int cursor = 0;
        int played = 0;
        for (MatchEvent sub : subs) {
            int at = Math.min(end, Math.max(cursor, clockSecond(sub, length)));
            if (onPitch) {
                played += at - cursor;
            }
            if (playerId.equals(sub.getPlayerId())) {
                onPitch = false;
            }
            if (playerId.equals(sub.getSecondaryPlayerId())) {
                onPitch = true;
            }
            cursor = at;
        }
        if (onPitch) {
            played += Math.max(0, end - cursor);
        }
        played = Math.min(played, capSeconds);
        if (played <= 0) {
            return starter ? 0 : null;
        }
        int minutes = played / 60;
        if (minutes == 0) {
            minutes = 1;
        }
        return Math.min(minutes, capSeconds / 60);
    }

    private static int clockSecond(MatchEvent event, int periodLengthSeconds) {
        int time = event.getGameTime() == null ? 0 : event.getGameTime();
        int period = event.getPeriod() == null ? 1 : event.getPeriod();
        return MatchClock.toKickoffSeconds(time, period, periodLengthSeconds);
    }
}
