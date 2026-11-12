package com.studentleague.matches.dto;

import java.util.UUID;

/** Who awarded a technical forfeit. Admin payloads only. */
public record ForfeitActorResponse(
        UUID id,
        String name
) {
}
