package com.studentleague.matches.dto;

import java.util.UUID;

/** {@code playerId} null clears the pick. */
public record SetPlayerOfTheMatchRequest(UUID playerId) {
}
