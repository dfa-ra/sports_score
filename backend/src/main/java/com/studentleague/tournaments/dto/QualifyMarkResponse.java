package com.studentleague.tournaments.dto;

import java.util.UUID;

/** Public place colour. OUT is omitted: those rows stay plain. */
public record QualifyMarkResponse(
        UUID groupId,
        int place,
        String stage
) {
}
