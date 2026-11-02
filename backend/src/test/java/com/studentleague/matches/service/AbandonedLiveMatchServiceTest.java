package com.studentleague.matches.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbandonedLiveMatchServiceTest {

    @Test
    void aMatchStillBeingWrittenIsNotIdle() {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        assertFalse(AbandonedLiveMatchService.idleSince(now.minus(Duration.ofMinutes(20)), now));
        assertFalse(AbandonedLiveMatchService.idleSince(now.minus(Duration.ofHours(5)), now));
    }

    @Test
    void sixQuietHoursIsAbandoned() {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        assertTrue(AbandonedLiveMatchService.idleSince(now.minus(Duration.ofHours(6)), now));
        assertTrue(AbandonedLiveMatchService.idleSince(now.minus(Duration.ofDays(13)), now));
    }

    @Test
    void missingActivityIsNotClosed() {
        assertFalse(AbandonedLiveMatchService.idleSince(null, Instant.parse("2026-11-02T12:00:00Z")));
        assertFalse(AbandonedLiveMatchService.idleSince(Instant.parse("2026-10-20T12:00:00Z"), null));
    }
}
