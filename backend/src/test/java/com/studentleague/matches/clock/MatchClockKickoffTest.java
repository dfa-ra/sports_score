package com.studentleague.matches.clock;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchClockKickoffTest {

    @Test
    void thirteenMinutesLeftInTheFirstHalfIsTwoMinutesFromKickoff() {
        Match match = paused(1, 15 * 60, 15 * 60 - 13 * 60);
        int kickoff = MatchClock.kickoffSeconds(match, Instant.parse("2026-10-04T12:00:00Z"));
        assertEquals(2 * 60, kickoff);
        assertEquals(2, MatchClock.displayMinute(kickoff));
    }

    @Test
    void twelveMinutesLeftInTheSecondHalfOfTwoByFifteenIsEighteen() {
        Match match = paused(2, 15 * 60, 15 * 60 - 12 * 60);
        int kickoff = MatchClock.kickoffSeconds(match, Instant.parse("2026-10-04T12:00:00Z"));
        assertEquals(18 * 60, kickoff);
        assertEquals(18, MatchClock.displayMinute(kickoff));
    }

    @Test
    void secondHalfUsesTheMatchPeriodLength() {
        Match match = paused(2, 20 * 60, 20 * 60 - 12 * 60);
        int kickoff = MatchClock.kickoffSeconds(match, Instant.parse("2026-10-04T12:00:00Z"));
        assertEquals(28 * 60, kickoff);
        assertEquals(28, MatchClock.displayMinute(kickoff));
    }

    @Test
    void legacyWithinPeriodRowsShiftOnceAndKickoffRowsStay() {
        assertEquals(2 * 60, MatchClock.toKickoffSeconds(2 * 60, 1, 15 * 60));
        assertEquals(18 * 60, MatchClock.toKickoffSeconds(3 * 60, 2, 15 * 60));
        assertEquals(18 * 60, MatchClock.toKickoffSeconds(18 * 60, 2, 15 * 60));
        assertEquals(30 * 60, MatchClock.toKickoffSeconds(15 * 60, 2, 15 * 60));
    }

    @Test
    void kickoffSecondsMapBackOntoThePeriodClock() {
        assertEquals(3 * 60, MatchClock.periodClockSeconds(18 * 60, 2, 15 * 60));
        assertEquals(3 * 60, MatchClock.periodClockSeconds(3 * 60, 2, 15 * 60));
        assertEquals(0, MatchClock.periodClockSeconds(15 * 60, 2, 15 * 60));
    }

    private static Match paused(int period, int periodLength, int elapsedInPeriod) {
        Match match = new Match();
        match.setStatus(MatchStatus.PAUSED);
        match.setPeriod(period);
        match.setPeriodLengthSeconds(periodLength);
        match.setGameTimeSeconds(elapsedInPeriod);
        return match;
    }
}
