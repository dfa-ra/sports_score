package com.studentleague.matches.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ForfeitSide {
    NONE("none"),
    HOME("home"),
    AWAY("away"),
    BOTH("both");

    private final String value;

    ForfeitSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ForfeitSide from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ForfeitSide side : values()) {
            if (side.value.equalsIgnoreCase(raw) || side.name().equalsIgnoreCase(raw)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Неизвестная сторона технического поражения");
    }
}
