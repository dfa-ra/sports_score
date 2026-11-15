package com.studentleague.matches.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ForfeitReason {
    NO_SHOW("no_show"),
    INCOMPLETE("incomplete"),
    OTHER("other");

    private final String value;

    ForfeitReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ForfeitReason from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ForfeitReason reason : values()) {
            if (reason.value.equalsIgnoreCase(raw) || reason.name().equalsIgnoreCase(raw)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Неизвестная причина технического поражения");
    }
}
