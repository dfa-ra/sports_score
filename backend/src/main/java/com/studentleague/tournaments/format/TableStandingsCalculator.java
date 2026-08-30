package com.studentleague.tournaments.format;

import com.studentleague.matches.entity.Match;
import com.studentleague.teams.entity.Team;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.entity.TournamentTeam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TableStandingsCalculator {

    public List<StandingRow> compute(StandingsContext context) {
        Map<UUID, Accumulator> table = new HashMap<>();
        for (TournamentTeam entry : context.entries()) {
            if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
                continue;
            }
            String name = context.teams().containsKey(entry.getTeamId())
                    ? context.teams().get(entry.getTeamId()).getName()
                    : "Unknown";
            table.put(entry.getTeamId(), new Accumulator(entry.getTeamId(), name));
        }
        for (Match match : context.finishedMatches()) {
            Accumulator home = table.computeIfAbsent(match.getHomeTeamId(),
                    id -> new Accumulator(id, teamName(context, id)));
            Accumulator away = table.computeIfAbsent(match.getAwayTeamId(),
                    id -> new Accumulator(id, teamName(context, id)));
            home.played++;
            away.played++;
            home.goalsFor += match.getHomeScore();
            home.goalsAgainst += match.getAwayScore();
            away.goalsFor += match.getAwayScore();
            away.goalsAgainst += match.getHomeScore();
            if (match.getHomeScore() > match.getAwayScore()) {
                home.wins++;
                home.points += 3;
                away.losses++;
            } else if (match.getHomeScore() < match.getAwayScore()) {
                away.wins++;
                away.points += 3;
                home.losses++;
            } else {
                home.draws++;
                away.draws++;
                home.points++;
                away.points++;
            }
        }
        return table.values().stream()
                .sorted(Comparator.comparingInt((Accumulator s) -> s.points).reversed()
                        .thenComparingInt(s -> s.goalsFor - s.goalsAgainst).reversed()
                        .thenComparing(s -> s.teamName))
                .map(s -> new StandingRow(
                        s.teamId, s.teamName, s.played, s.wins, s.draws, s.losses,
                        s.goalsFor, s.goalsAgainst, s.points))
                .toList();
    }

    private static String teamName(StandingsContext context, UUID teamId) {
        Team team = context.teams().get(teamId);
        return team == null ? "Unknown" : team.getName();
    }

    private static final class Accumulator {
        private final UUID teamId;
        private final String teamName;
        private int played;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private int points;

        private Accumulator(UUID teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
        }
    }
}
