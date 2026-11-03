package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KickoffRangeTest {

    @Test
    void blankIsOpenEnded() {
        assertNull(KickoffRange.start(" "));
        assertNull(KickoffRange.end(null));
    }

    @Test
    void instantIsInclusiveAsWritten() {
        Instant instant = Instant.parse("2099-06-15T12:00:00Z");
        assertEquals(instant, KickoffRange.start("2099-06-15T12:00:00Z"));
        assertEquals(instant, KickoffRange.end("2099-06-15T12:00:00Z"));
    }

    @Test
    void dateOnlyIsAMoscowCivilDay() {
        assertEquals(Instant.parse("2099-06-14T21:00:00Z"), KickoffRange.start("2099-06-15"));
        assertEquals(Instant.parse("2099-06-15T20:59:59.999999999Z"), KickoffRange.end("2099-06-15"));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(ApiException.class, () -> KickoffRange.start("soon"));
        assertThrows(ApiException.class, () -> KickoffRange.end("2099-13-40"));
    }
}
