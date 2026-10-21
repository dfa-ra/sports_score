package com.studentleague.matches.scoring;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** A GOAL whose point belongs to the other team. The player is the one who put it in. */
public final class OwnGoals {

    private OwnGoals() {
    }

    public static boolean is(MatchEvent event) {
        return event != null
                && event.getEventType() == MatchEventType.GOAL
                && is(event.getMetadata());
    }

    public static boolean is(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        Object value = metadata.get("ownGoal");
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /** Writes or clears {@code ownGoal}. An own goal never keeps an assist. */
    public static Map<String, Object> apply(Map<String, Object> metadata, boolean ownGoal) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (metadata != null) {
            copy.putAll(metadata);
        }
        if (ownGoal) {
            copy.put("ownGoal", Boolean.TRUE);
        } else {
            copy.remove("ownGoal");
        }
        return copy.isEmpty() ? null : copy;
    }
}
