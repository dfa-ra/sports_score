package com.studentleague.matches.shootout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Playoff shootout. The first five rounds are the base: a side is out only when
 * the other cannot be caught with the kicks still left in those five. From the
 * sixth round both sides kick, and the series ends on the first miss that
 * breaks the tie. There is no upper limit on rounds.
 */
public final class ShootoutRules {

    public static final int BASE_ROUNDS = 5;

    private ShootoutRules() {
    }

    public record Kick(UUID teamId, boolean scored) {
    }

    public record Tally(int homeTaken, int awayTaken, int homeScored, int awayScored) {
    }

    public record ScoredKick(UUID matchId, UUID teamId, boolean scored) {
    }

    public enum Problem {
        NONE,
        WRONG_TEAM,
        ALREADY_DECIDED,
        EMPTY,
        TIED,
        NOT_FINISHED
    }

    public static int roundOf(int kickOrder) {
        return (kickOrder + 1) / 2;
    }

    public static Tally tally(UUID homeTeamId, List<Kick> kicks) {
        int homeTaken = 0;
        int awayTaken = 0;
        int homeScored = 0;
        int awayScored = 0;
        for (Kick kick : kicks) {
            if (homeTeamId.equals(kick.teamId())) {
                homeTaken++;
                if (kick.scored()) {
                    homeScored++;
                }
            } else {
                awayTaken++;
                if (kick.scored()) {
                    awayScored++;
                }
            }
        }
        return new Tally(homeTaken, awayTaken, homeScored, awayScored);
    }

    public static boolean decisive(UUID homeTeamId, UUID awayTeamId, List<Kick> kicks) {
        if (kicks.isEmpty()) {
            return false;
        }
        Tally tally = tally(homeTeamId, kicks);
        if (tally.homeTaken() != tally.awayTaken()) {
            if (tally.homeTaken() > BASE_ROUNDS || tally.awayTaken() > BASE_ROUNDS) {
                return false;
            }
            return leadIsFinal(tally);
        }
        if (tally.homeScored() == tally.awayScored()) {
            return false;
        }
        if (tally.homeTaken() < BASE_ROUNDS) {
            return leadIsFinal(tally);
        }
        return true;
    }

    public static UUID winner(UUID homeTeamId, UUID awayTeamId, List<Kick> kicks) {
        if (!decisive(homeTeamId, awayTeamId, kicks)) {
            return null;
        }
        Tally tally = tally(homeTeamId, kicks);
        if (tally.homeScored() > tally.awayScored()) {
            return homeTeamId;
        }
        if (tally.awayScored() > tally.homeScored()) {
            return awayTeamId;
        }
        return null;
    }

    public static UUID teamToKick(UUID homeTeamId, UUID awayTeamId, List<Kick> kicks) {
        if (decisive(homeTeamId, awayTeamId, kicks)) {
            return null;
        }
        Tally tally = tally(homeTeamId, kicks);
        if (tally.homeTaken() == tally.awayTaken()) {
            return homeTeamId;
        }
        if (tally.homeTaken() == tally.awayTaken() + 1) {
            return awayTeamId;
        }
        return null;
    }

    public static Problem appendProblem(UUID homeTeamId, UUID awayTeamId, List<Kick> existing, UUID teamId) {
        if (teamId == null || (!teamId.equals(homeTeamId) && !teamId.equals(awayTeamId))) {
            return Problem.WRONG_TEAM;
        }
        if (decisive(homeTeamId, awayTeamId, existing)) {
            return Problem.ALREADY_DECIDED;
        }
        UUID turn = teamToKick(homeTeamId, awayTeamId, existing);
        if (turn == null || !turn.equals(teamId)) {
            return Problem.WRONG_TEAM;
        }
        return Problem.NONE;
    }

    public static Problem seriesProblem(UUID homeTeamId, UUID awayTeamId, List<Kick> kicks) {
        if (kicks == null || kicks.isEmpty()) {
            return Problem.EMPTY;
        }
        List<Kick> accepted = new ArrayList<>();
        for (Kick kick : kicks) {
            Problem step = appendProblem(homeTeamId, awayTeamId, accepted, kick.teamId());
            if (step != Problem.NONE) {
                return step;
            }
            accepted.add(kick);
        }
        if (decisive(homeTeamId, awayTeamId, accepted)) {
            return Problem.NONE;
        }
        Tally tally = tally(homeTeamId, accepted);
        if (tally.homeScored() == tally.awayScored()) {
            return Problem.TIED;
        }
        return Problem.NOT_FINISHED;
    }

    public static String message(Problem problem) {
        return switch (problem) {
            case WRONG_TEAM -> "Сейчас бьёт другая команда";
            case ALREADY_DECIDED -> "Серия пенальти уже решена";
            case EMPTY -> "Серия пенальти пуста";
            case TIED -> "Серия пенальти не может закончиться вничью";
            case NOT_FINISHED -> "Серия пенальти ещё не решена";
            case NONE -> "";
        };
    }

    public static Map<UUID, Tally> byMatch(List<ScoredKick> kicks, Map<UUID, UUID> homeTeamByMatch) {
        Map<UUID, List<Kick>> grouped = new HashMap<>();
        for (ScoredKick kick : kicks) {
            grouped.computeIfAbsent(kick.matchId(), ignored -> new ArrayList<>())
                    .add(new Kick(kick.teamId(), kick.scored()));
        }
        Map<UUID, Tally> result = new HashMap<>();
        for (Map.Entry<UUID, List<Kick>> entry : grouped.entrySet()) {
            UUID homeTeamId = homeTeamByMatch.get(entry.getKey());
            if (homeTeamId == null) {
                continue;
            }
            result.put(entry.getKey(), tally(homeTeamId, entry.getValue()));
        }
        return result;
    }

    private static boolean leadIsFinal(Tally tally) {
        int homeLeft = Math.max(0, BASE_ROUNDS - tally.homeTaken());
        int awayLeft = Math.max(0, BASE_ROUNDS - tally.awayTaken());
        return tally.awayScored() + awayLeft < tally.homeScored()
                || tally.homeScored() + homeLeft < tally.awayScored();
    }
}
