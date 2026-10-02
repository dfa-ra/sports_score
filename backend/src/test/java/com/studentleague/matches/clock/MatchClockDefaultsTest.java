package com.studentleague.matches.clock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchClockDefaultsTest {

    @Test
    void newMatchDefaultIsTwoHalvesOfFifteenMinutes() {
        assertEquals(2, MatchClock.DEFAULT_PERIOD_COUNT);
        assertEquals(15 * 60, MatchClock.DEFAULT_PERIOD_LENGTH_SECONDS);
    }
}
