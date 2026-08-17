package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;

import java.time.Duration;
import java.time.Instant;

public final class MatchClock {

    public static final int DEFAULT_PERIOD_COUNT = 2;
    public static final int DEFAULT_PERIOD_LENGTH_SECONDS = 20 * 60;

    private MatchClock() {
    }

    public static int elapsedSeconds(Match match, Instant now) {
        int base = match.getGameTimeSeconds() == null ? 0 : match.getGameTimeSeconds();
        if (match.getStatus() == MatchStatus.LIVE && match.getClockRunningSince() != null) {
            long extra = Duration.between(match.getClockRunningSince(), now).getSeconds();
            base += (int) Math.max(0, extra);
        }
        int cap = match.getPeriodLengthSeconds() > 0
                ? match.getPeriodLengthSeconds()
                : DEFAULT_PERIOD_LENGTH_SECONDS;
        return Math.min(Math.max(0, base), cap);
    }

    public static void freeze(Match match, Instant now) {
        match.setGameTimeSeconds(elapsedSeconds(match, now));
        match.setClockRunningSince(null);
    }

    public static void startRunning(Match match, Instant now) {
        match.setClockRunningSince(now);
    }
}
