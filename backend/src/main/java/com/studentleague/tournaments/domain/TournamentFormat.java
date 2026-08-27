package com.studentleague.tournaments.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Built-in formats. New ones can be added here plus a {@code TournamentFormatHandler} bean.
 */
public enum TournamentFormat {
    ROUND_ROBIN("Круговой турнир"),
    CUP("Кубок / плей-офф"),
    GROUPS_PLAYOFF("Группы + плей-офф"),
    SWISS("Швейцарская система"),
    DOUBLE_ELIMINATION("Double elimination");

    private final String title;

    TournamentFormat(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public static Optional<TournamentFormat> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized)
                        || ("KNOCKOUT".equals(normalized) && value == CUP)
                        || ("GROUPS".equals(normalized) && value == GROUPS_PLAYOFF))
                .findFirst();
    }

    public static String normalize(String code) {
        return fromCode(code).map(Enum::name).orElse(code == null ? ROUND_ROBIN.name() : code.trim());
    }
}
