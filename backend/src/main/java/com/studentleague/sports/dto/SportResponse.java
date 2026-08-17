package com.studentleague.sports.dto;

import java.util.UUID;

public record SportResponse(UUID id, String name, String code) {
}
