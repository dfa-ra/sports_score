package com.studentleague.tournaments.format;

import com.studentleague.matches.entity.Match;
import com.studentleague.teams.entity.Team;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StandingsContext(
        Tournament tournament,
        List<TournamentTeam> entries,
        List<Match> finishedMatches,
        Map<UUID, Team> teams
) {
}
