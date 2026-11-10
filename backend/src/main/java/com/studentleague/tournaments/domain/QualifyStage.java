package com.studentleague.tournaments.domain;

/**
 * Where a group place goes. {@link #F} is derived from the semifinal
 * and is not a choice on the admin row.
 */
public enum QualifyStage {
    R16,
    R8,
    QF,
    SF,
    F,
    SILVER,
    OUT;

    public boolean playoffEntry() {
        return this == R16 || this == R8 || this == QF || this == SF;
    }

    public String label() {
        return switch (this) {
            case R16 -> "1/16";
            case R8 -> "1/8";
            case QF -> "1/4";
            case SF -> "1/2";
            case F -> "Финал";
            case SILVER -> "Серебряная лига";
            case OUT -> "Выбывает";
        };
    }

    /** Stages a direct place may be assigned to. Final is not one of them. */
    public static QualifyStage direct(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            QualifyStage stage = QualifyStage.valueOf(raw.trim());
            return stage == F ? null : stage;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
