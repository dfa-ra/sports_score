package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * {@code from} / {@code to} on {@code GET /matches} bound {@code scheduledAt}.
 * A bare {@code YYYY-MM-DD} is a Europe/Moscow civil day. An ISO-8601 instant is used as given.
 * Both ends are inclusive.
 */
public final class KickoffRange {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private KickoffRange() {
    }

    public static Instant start(String raw) {
        return bound(raw, true);
    }

    public static Instant end(String raw) {
        return bound(raw, false);
    }

    private static Instant bound(String raw, boolean start) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        String name = start ? "from" : "to";
        if (DATE.matcher(text).matches()) {
            try {
                LocalDate day = LocalDate.parse(text);
                Instant dayStart = day.atStartOfDay(MOSCOW).toInstant();
                if (start) {
                    return dayStart;
                }
                return day.plusDays(1).atStartOfDay(MOSCOW).toInstant().minusNanos(1);
            } catch (DateTimeException ex) {
                throw ApiException.badRequest("Параметр " + name + " должен быть датой YYYY-MM-DD или моментом ISO-8601");
            }
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("Параметр " + name + " должен быть датой YYYY-MM-DD или моментом ISO-8601");
        }
    }
}
