package com.studentleague.tournaments.format;

import com.studentleague.tournaments.dto.StandingRow;

import java.util.List;

public interface TournamentFormatHandler {

    String code();

    String title();

    String description();

    List<StandingRow> standings(StandingsContext context);
}
