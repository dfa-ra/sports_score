package com.studentleague.players.dto;

import com.studentleague.common.PersonNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PlayerProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = PersonNames.PLAIN, message = PersonNames.MESSAGE) String firstName,
        @NotBlank @Size(max = 100) @Pattern(regexp = PersonNames.PLAIN, message = PersonNames.MESSAGE) String lastName,
        @Size(max = 150) @Pattern(regexp = PersonNames.PLAIN, message = PersonNames.MESSAGE) String displayName,
        LocalDate dateOfBirth,
        String avatarUrl,
        Integer jerseyNumber,
        @Size(max = 64) String position,
        String bio
) {
}
