package com.studentleague.tournaments.format;

import com.studentleague.matches.entity.Match;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.entity.TournamentTeam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

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
        List<Match> finished = context.finishedMatches();
        for (Match match : finished) {
            Accumulator home = table.get(match.getHomeTeamId());
            Accumulator away = table.get(match.getAwayTeamId());
            if (home == null || away == null) {
                continue;
            }
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
        return rank(new ArrayList<>(table.values()), finished).stream()
                .map(team -> new StandingRow(
                        team.teamId, team.teamName, team.played, team.wins, team.draws, team.losses,
                        team.goalsFor, team.goalsAgainst, team.points))
                .toList();
    }

    private List<Accumulator> rank(List<Accumulator> teams, List<Match> finished) {
        List<Accumulator> ordered = new ArrayList<>();
        for (List<Accumulator> level : groupsBy(teams, team -> team.points)) {
            ordered.addAll(breakHeadToHead(level, finished));
        }
        return ordered;
    }

    /**
     * Teams in {@code level} are tied on overall points, or on head-to-head points of a wider group.
     * Only matches with both sides still in this subset count. If those points split the subset,
     * the same head-to-head procedure runs again inside each remaining tie.
     */
    private List<Accumulator> breakHeadToHead(List<Accumulator> level, List<Match> finished) {
        if (level.size() <= 1) {
            return level;
        }
        Map<UUID, HeadToHead> headToHead = headToHead(level, finished);
        List<List<Accumulator>> byPoints = groupsBy(level, team -> headToHead.get(team.teamId).points);
        if (byPoints.size() > 1) {
            List<Accumulator> ordered = new ArrayList<>();
            for (List<Accumulator> subset : byPoints) {
                ordered.addAll(breakHeadToHead(subset, finished));
            }
            return ordered;
        }
        return level.stream().sorted(afterEqualHeadToHeadPoints(headToHead)).toList();
    }

    private Comparator<Accumulator> afterEqualHeadToHeadPoints(Map<UUID, HeadToHead> headToHead) {
        return Comparator
                .comparingInt((Accumulator team) -> headToHead.get(team.teamId).goalDifference()).reversed()
                .thenComparing(Comparator.comparingInt((Accumulator team) -> headToHead.get(team.teamId).goalsFor).reversed())
                .thenComparing(Comparator.comparingInt((Accumulator team) -> team.goalsFor - team.goalsAgainst).reversed())
                .thenComparing(Comparator.comparingInt((Accumulator team) -> team.goalsFor).reversed())
                .thenComparing(team -> team.teamName);
    }

    private Map<UUID, HeadToHead> headToHead(List<Accumulator> level, List<Match> finished) {
        Set<UUID> ids = level.stream().map(team -> team.teamId).collect(Collectors.toSet());
        Map<UUID, HeadToHead> mini = new HashMap<>();
        for (Accumulator team : level) {
            mini.put(team.teamId, new HeadToHead());
        }
        for (Match match : finished) {
            if (!ids.contains(match.getHomeTeamId()) || !ids.contains(match.getAwayTeamId())) {
                continue;
            }
            HeadToHead home = mini.get(match.getHomeTeamId());
            HeadToHead away = mini.get(match.getAwayTeamId());
            home.goalsFor += match.getHomeScore();
            home.goalsAgainst += match.getAwayScore();
            away.goalsFor += match.getAwayScore();
            away.goalsAgainst += match.getHomeScore();
            if (match.getHomeScore() > match.getAwayScore()) {
                home.points += 3;
            } else if (match.getHomeScore() < match.getAwayScore()) {
                away.points += 3;
            } else {
                home.points++;
                away.points++;
            }
        }
        return mini;
    }

    private List<List<Accumulator>> groupsBy(List<Accumulator> teams, ToIntFunction<Accumulator> pointsOf) {
        List<Accumulator> sorted = teams.stream()
                .sorted(Comparator.comparingInt(pointsOf).reversed().thenComparing(team -> team.teamName))
                .toList();
        List<List<Accumulator>> groups = new ArrayList<>();
        List<Accumulator> current = new ArrayList<>();
        Integer points = null;
        for (Accumulator team : sorted) {
            int teamPoints = pointsOf.applyAsInt(team);
            if (points != null && teamPoints != points) {
                groups.add(current);
                current = new ArrayList<>();
            }
            points = teamPoints;
            current.add(team);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private static final class HeadToHead {
        private int points;
        private int goalsFor;
        private int goalsAgainst;

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }
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
