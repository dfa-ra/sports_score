package com.studentleague.tournaments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TournamentCopyTest {

    private static final String DEMO = "Демо-сезон, чтобы таблица и статистика не были пустыми.";

    @Test
    void dropsTheOldDemoSeasonSentence() {
        assertNull(TournamentCopy.storedDescription(DEMO));
        assertNull(TournamentCopy.storedDescription("  " + DEMO + "  "));
        assertNull(TournamentCopy.storedDescription("  демо-сезон для проверки  "));
        assertNull(TournamentCopy.storedDescription("Демо-сезон\nчтобы не было пусто"));
    }

    @Test
    void keepsARealDescription() {
        assertEquals("Студенческий кубок", TournamentCopy.storedDescription("Студенческий кубок"));
        assertEquals("Кубок факультетов.", TournamentCopy.storedDescription("Кубок факультетов. " + DEMO));
        assertEquals(
                "Сезон 2026. Игры по субботам.",
                TournamentCopy.storedDescription("Сезон 2026. " + DEMO + " Игры по субботам.")
        );
        assertNull(TournamentCopy.storedDescription("  "));
        assertNull(TournamentCopy.storedDescription(null));
    }
}
