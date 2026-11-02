package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchEventType;
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

    /**
     * Match minute from kickoff seconds. {@code 2:00} is {@code 2}.
     * The opening of a later period is the next minute: six seconds into the
     * second half of a 2×15 ({@code 15:06}) is {@code 16'}, while an exact
     * {@code 15:00} stays {@code 15'} unless {@link #protocolMinute} is marking
     * the start of that period.
     */
    public static int displayMinute(int kickoffSeconds) {
        return displayMinute(kickoffSeconds, DEFAULT_PERIOD_LENGTH_SECONDS);
    }

    /** Live list minute: same labels as the protocol, using this match's period length. */
    public static int liveMinute(Match match, Instant now) {
        int length = periodLength(match);
        int seconds = kickoffSeconds(match, now);
        int period = periodIndex(match);
        int minute = displayMinute(seconds, length);
        if (period > 1 && seconds == (period - 1L) * length) {
            return minute + 1;
        }
        return minute;
    }

    public static int displayMinute(int kickoffSeconds, int periodLengthSeconds) {
        int seconds = Math.max(0, kickoffSeconds);
        int length = periodLengthSeconds > 0 ? periodLengthSeconds : DEFAULT_PERIOD_LENGTH_SECONDS;
        int minute = seconds / 60;
        int intoPeriod = seconds % length;
        if (seconds >= length && intoPeriod > 0 && intoPeriod < 60) {
            return minute + 1;
        }
        return minute;
    }

    /**
     * Minute printed on a protocol row. Kickoff ({@code 0}) is {@code 1'}.
     * A period that ended after 26 seconds is {@code 1'} on both the end and
     * the next start. A full 2×15 whistle is {@code 15'} for the end of the
     * first half and {@code 16'} for the start of the second.
     */
    public static int protocolMinute(int gameTime, Integer period, MatchEventType type, int periodLengthSeconds) {
        int seconds = Math.max(0, gameTime);
        int length = periodLengthSeconds > 0 ? periodLengthSeconds : DEFAULT_PERIOD_LENGTH_SECONDS;
        boolean boundary = type == MatchEventType.PERIOD_START || type == MatchEventType.PERIOD_END;
        if (boundary && seconds < 60) {
            return 1;
        }
        int minute = displayMinute(seconds, length);
        if (type == MatchEventType.PERIOD_START && period != null && period > 1) {
            int offset = (period - 1) * length;
            if (seconds == offset) {
                return minute + 1;
            }
        }
        return minute;
    }

    public static void freeze(Match match, Instant now) {
        match.setGameTimeSeconds(elapsedSeconds(match, now));
        match.setClockRunningSince(null);
    }

    public static void startRunning(Match match, Instant now) {
        match.setClockRunningSince(now);
    }
}
