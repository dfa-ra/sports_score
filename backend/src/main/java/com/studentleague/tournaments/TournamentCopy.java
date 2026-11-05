package com.studentleague.tournaments;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The old demo seeder stored a blurb on the cup. That sentence, and any
 * description that is itself a «Демо-сезон…» line, is not shown and is not
 * kept when a tournament is saved. Text around the sentence stays.
 */
public final class TournamentCopy {

    private static final Pattern EXACT_SENTENCE = Pattern.compile(
            "демо-сезон,\\s*чтобы таблица и статистика не были пустыми\\.?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DEMO_VARIANT = Pattern.compile(
            "демо-сезон[^\\n.]*(?:\\.(?=\\s|$)|(?=\\n)|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Locale RU = Locale.forLanguageTag("ru");

    private TournamentCopy() {
    }

    public static String storedDescription(String value) {
        if (value == null) {
            return null;
        }
        String original = value.trim();
        if (original.isEmpty()) {
            return null;
        }
        String text = EXACT_SENTENCE.matcher(original).replaceAll(" ");
        if (startsWithDemoSeason(text)) {
            return null;
        }
        text = DEMO_VARIANT.matcher(text).replaceAll(" ");
        text = text
                .replaceAll("[^\\S\\n]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("\\s+([,.;:!?])", "$1")
                .replaceAll("^\\s*[.,;:]+\\s*", "")
                .trim();
        if (text.isEmpty() || startsWithDemoSeason(text)) {
            return null;
        }
        return text;
    }

    private static boolean startsWithDemoSeason(String value) {
        return value.trim().toLowerCase(RU).startsWith("демо-сезон");
    }
}
