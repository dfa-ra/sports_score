package com.studentleague.tournaments.dto;

/** One of the last four results. {@code result} is W, D or L. {@code technical} marks a forfeit loss. */
public record StandingFormMark(
        String result,
        boolean technical
) {
}
