package com.studentleague.teams.service;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.teams.dto.TeamPlayerStatResponse;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Current-squad totals for one team. Goals, assists and cards come from events
 * whose team is this club. A game is a finished match where the player was in
 * this team's lineup. If that match has no lineup, a goal, assist or card is
 * enough, so older protocols are not a column of zeros.
 */
public final class TeamSquadStats {

    private TeamSquadStats() {
    }

    public record Player(UUID playerId, String firstName, String lastName, String photoUrl) {
    }

    public static List<TeamPlayerStatResponse> aggregate(
            UUID teamId,
            List<Player> squad,
            List<Match> matches,
            List<MatchEvent> events,
            List<MatchLineupPlayer> lineups
    ) {
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        Map<UUID, Acc> stats = new HashMap<>();
        List<Player> players = new ArrayList<>();
        for (Player player : squad) {
            if (player == null || player.playerId() == null || stats.containsKey(player.playerId())) {
                continue;
            }
            stats.put(player.playerId(), new Acc());
            players.add(player);
        }
        if (players.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<MatchEvent>> eventsByMatch = new HashMap<>();
        if (events != null) {
            for (MatchEvent event : events) {
                if (event == null || event.getMatchId() == null || event.isVoided()) {
                    continue;
                }
                eventsByMatch.computeIfAbsent(event.getMatchId(), id -> new ArrayList<>()).add(event);
            }
        }
        Map<UUID, Set<UUID>> lineupByMatch = new HashMap<>();
        if (lineups != null) {
            for (MatchLineupPlayer row : lineups) {
                if (row == null || row.getMatchId() == null || row.getPlayerId() == null) {
                    continue;
                }
                if (!teamId.equals(row.getTeamId())) {
                    continue;
                }
                lineupByMatch.computeIfAbsent(row.getMatchId(), id -> new HashSet<>()).add(row.getPlayerId());
            }
        }

        if (matches != null) {
            for (Match match : matches) {
                if (match == null || match.getId() == null || match.getStatus() != MatchStatus.FINISHED) {
                    continue;
                }
                if (!teamId.equals(match.getHomeTeamId()) && !teamId.equals(match.getAwayTeamId())) {
                    continue;
                }
                Set<UUID> linedUp = lineupByMatch.getOrDefault(match.getId(), Set.of());
                boolean lineupSubmitted = !linedUp.isEmpty();
                if (lineupSubmitted) {
                    for (UUID playerId : linedUp) {
                        Acc acc = stats.get(playerId);
                        if (acc != null) {
                            acc.appearances++;
                        }
                    }
                }
                applyEvents(teamId, stats, eventsByMatch.getOrDefault(match.getId(), List.of()), !lineupSubmitted);
            }
        }

        Collator names = Collator.getInstance(Locale.forLanguageTag("ru"));
        names.setStrength(Collator.PRIMARY);
        return players.stream()
                .map(player -> {
                    Acc acc = stats.get(player.playerId());
                    return new TeamPlayerStatResponse(
                            player.playerId(),
                            player.firstName(),
                            player.lastName(),
                            player.photoUrl(),
                            acc.goals,
                            acc.assists,
                            acc.appearances,
                            acc.yellowCards
                    );
                })
                .sorted(Comparator.comparingLong(TeamPlayerStatResponse::goals).reversed()
                        .thenComparing(Comparator.comparingLong(TeamPlayerStatResponse::assists).reversed())
                        .thenComparing(Comparator.comparingLong(TeamPlayerStatResponse::appearances).reversed())
                        .thenComparing(TeamSquadStats::registeredName, names))
                .toList();
    }

    private static void applyEvents(UUID teamId, Map<UUID, Acc> stats, List<MatchEvent> events, boolean countAppearance) {
        Set<UUID> involved = new HashSet<>();
        for (MatchEvent event : events) {
            if (event.getTeamId() == null || !teamId.equals(event.getTeamId())) {
                continue;
            }
            switch (event.getEventType()) {
                case GOAL -> {
                    if (!ownGoal(event)) {
                        add(stats, event.getPlayerId(), AccField.GOAL, involved);
                        add(stats, event.getSecondaryPlayerId(), AccField.ASSIST, involved);
                    } else {
                        mark(stats, event.getPlayerId(), involved);
                    }
                }
                case ASSIST -> add(stats, event.getPlayerId(), AccField.ASSIST, involved);
                case YELLOW_CARD -> add(stats, event.getPlayerId(), AccField.YELLOW, involved);
                case RED_CARD -> mark(stats, event.getPlayerId(), involved);
                default -> {
                }
            }
        }
        if (!countAppearance) {
            return;
        }
        for (UUID playerId : involved) {
            stats.get(playerId).appearances++;
        }
    }

    private static void add(Map<UUID, Acc> stats, UUID playerId, AccField field, Set<UUID> involved) {
        Acc acc = playerId == null ? null : stats.get(playerId);
        if (acc == null) {
            return;
        }
        switch (field) {
            case GOAL -> acc.goals++;
            case ASSIST -> acc.assists++;
            case YELLOW -> acc.yellowCards++;
        }
        involved.add(playerId);
    }

    private static void mark(Map<UUID, Acc> stats, UUID playerId, Set<UUID> involved) {
        if (playerId != null && stats.containsKey(playerId)) {
            involved.add(playerId);
        }
    }

    private static boolean ownGoal(MatchEvent event) {
        return event.getEventType() == MatchEventType.GOAL
                && event.getMetadata() != null
                && Boolean.TRUE.equals(event.getMetadata().get("ownGoal"));
    }

    private static String registeredName(TeamPlayerStatResponse row) {
        String last = row.lastName() == null ? "" : row.lastName().trim();
        String first = row.firstName() == null ? "" : row.firstName().trim();
        return (last + " " + first).trim();
    }

    private enum AccField {
        GOAL, ASSIST, YELLOW
    }

    private static final class Acc {
        private long goals;
        private long assists;
        private long appearances;
        private long yellowCards;
    }
}
