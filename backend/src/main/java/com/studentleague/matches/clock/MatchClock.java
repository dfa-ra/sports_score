package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;

import java.time.Duration;
import java.time.Instant;

public final class MatchClock {

    public static final int DEFAULT_PERIOD_COUNT = 2;
    public static final int DEFAULT_PERIOD_LENGTH_SECONDS = 15 * 60;

    private MatchClock() {
    }

    /**
     * Count-up seconds inside the current period, capped at the period length.
     * The referee pad draws the hall countdown ({@code periodLength - elapsed}),
     * but this value is time already played in the period, not time remaining.
     */
    public static int elapsedSeconds(Match match, Instant now) {
        int base = match.getGameTimeSeconds() == null ? 0 : match.getGameTimeSeconds();
        if (match.getStatus() == MatchStatus.LIVE && match.getClockRunningSince() != null) {
            long extra = Duration.between(match.getClockRunningSince(), now).getSeconds();
            base += (int) Math.max(0, extra);
        }
        return Math.min(Math.max(0, base), periodLength(match));
    }

    public static int periodLength(Match match) {
        return match.getPeriodLengthSeconds() > 0
                ? match.getPeriodLengthSeconds()
                : DEFAULT_PERIOD_LENGTH_SECONDS;
    }

    public static int periodIndex(Match match) {
        int period = match.getPeriod() == null ? 1 : match.getPeriod();
        return Math.max(1, period);
    }

    /**
     * Elapsed seconds from kickoff: full previous periods plus the count-up clock
     * of the current period. A 2×15 match with 3:00 played in the second half is
     * 18:00 (1080 seconds), not 3:00 and not the 12:00 still on the countdown.
     */
    public static int kickoffSeconds(Match match, Instant now) {
        int length = periodLength(match);
        return (periodIndex(match) - 1) * length + elapsedSeconds(match, now);
    }

    /**
     * Referee rows written before 0.2.34 stored the count-up clock inside the
     * period ({@code 0..periodLength}). Admin rows and anything written from
     * 0.2.34 already store seconds from kickoff, which for a later period is
     * greater than one period — except a value sitting exactly on the boundary.
     * Do not call this on a value {@link #kickoffSeconds} just produced: a goal
     * at 0:00 of the second half is exactly {@code periodLength} and is already
     * from kickoff.
     */
    public static int toKickoffSeconds(int gameTime, int period, int periodLengthSeconds) {
        int length = periodLengthSeconds > 0 ? periodLengthSeconds : DEFAULT_PERIOD_LENGTH_SECONDS;
        int time = Math.max(0, gameTime);
        int safePeriod = Math.max(1, period);
        if (safePeriod > 1 && time <= length) {
            return time + (safePeriod - 1) * length;
        }
        return time;
    }

    /**
     * Map a stored event time back onto the count-up clock of its own period
     * so it can be compared with {@link #elapsedSeconds}. Kickoff-based rows
     * (value at least the start of that period) lose the previous periods.
     * Legacy within-period rows are already on that clock.
     */
    public static int periodClockSeconds(int gameTime, int period, int periodLengthSeconds) {
        int length = periodLengthSeconds > 0 ? periodLengthSeconds : DEFAULT_PERIOD_LENGTH_SECONDS;
        int time = Math.max(0, gameTime);
        int safePeriod = Math.max(1, period);
        int offset = (safePeriod - 1) * length;
        if (safePeriod > 1 && time >= offset) {
            return time - offset;
        }
        return time;
    }

    /** Match minute shown to viewers: {@code 2:00} from kickoff is {@code 2}. */
    public static int displayMinute(int kickoffSeconds) {
        return Math.max(0, kickoffSeconds) / 60;
    }

    public static void freeze(Match match, Instant now) {
        match.setGameTimeSeconds(elapsedSeconds(match, now));
        match.setClockRunningSince(null);
    }

    public static void startRunning(Match match, Instant now) {
        match.setClockRunningSince(now);
    }
}
