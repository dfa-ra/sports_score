package com.studentleague.matches.futsal;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.dto.FutsalBoardResponse;
import com.studentleague.matches.dto.FutsalBoardResponse.TeamFutsalState;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FutsalBoardCalculator {

    public static final int TEN_METER_FROM_FOUL = 6;
    public static final int SHORT_HANDED_SECONDS = 120;
    public static final String PERIOD_SCOPE = "PERIOD";
    public static final String MATCH_SCOPE = "MATCH";
    public static final String PERIOD_NOTE =
            "Фолы и тайм-аут считаются в текущем тайме и сбрасываются со сменой тайма.";
    public static final String MATCH_NOTE =
            "В модели нет таймов — фолы и тайм-аут считаются на весь матч.";

    private FutsalBoardCalculator() {
    }

    public static boolean periodScoped(Match match) {
        return match.getPeriodCount() > 0;
    }

    public static FutsalBoardResponse build(Match match, List<MatchEvent> events, int elapsedSeconds) {
        boolean scoped = periodScoped(match);
        List<MatchEvent> active = events.stream()
                .filter(event -> inScope(event, scoped, match.getPeriod()))
                .toList();
        return new FutsalBoardResponse(
                scoped ? PERIOD_SCOPE : MATCH_SCOPE,
                match.getPeriod(),
                scoped ? PERIOD_NOTE : MATCH_NOTE,
                List.of(
                        teamState(active, match.getHomeTeamId(), match.getAwayTeamId(), elapsedSeconds),
                        teamState(active, match.getAwayTeamId(), match.getHomeTeamId(), elapsedSeconds)
                )
        );
    }

    public static int count(List<MatchEvent> events, MatchEventType type, UUID teamId, Match match) {
        int total = 0;
        boolean scoped = periodScoped(match);
        for (MatchEvent event : events) {
            if (event.getEventType() != type || !teamId.equals(event.getTeamId())) {
                continue;
            }
            if (inScope(event, scoped, match.getPeriod())) {
                total++;
            }
        }
        return total;
    }

    public static boolean timeoutUsed(List<MatchEvent> events, UUID teamId, Match match) {
        return count(events, MatchEventType.TIMEOUT, teamId, match) > 0;
    }

    public static String timeoutRejectedMessage(Match match) {
        if (periodScoped(match)) {
            return "Тайм-аут в этом тайме уже взят";
        }
        return "Тайм-аут в этом матче уже взят";
    }

    public static Map<String, Object> foulMetadata(
            Map<String, Object> incoming,
            List<MatchEvent> events,
            UUID teamId,
            Match match
    ) {
        int next = count(events, MatchEventType.FOUL, teamId, match) + 1;
        if (next < TEN_METER_FROM_FOUL) {
            return incoming;
        }
        Map<String, Object> metadata = incoming == null ? new LinkedHashMap<>() : new LinkedHashMap<>(incoming);
        metadata.put("tenMeters", Boolean.TRUE);
        return metadata;
    }

    private static TeamFutsalState teamState(
            List<MatchEvent> active,
            UUID teamId,
            UUID opponentId,
            int elapsedSeconds
    ) {
        int fouls = 0;
        boolean timeoutUsed = false;
        for (MatchEvent event : active) {
            if (!teamId.equals(event.getTeamId())) {
                continue;
            }
            if (event.getEventType() == MatchEventType.FOUL) {
                fouls++;
            } else if (event.getEventType() == MatchEventType.TIMEOUT) {
                timeoutUsed = true;
            }
        }
        ShortHanded shortHanded = shortHanded(active, teamId, opponentId, elapsedSeconds);
        return new TeamFutsalState(
                teamId,
                fouls,
                fouls >= TEN_METER_FROM_FOUL,
                !timeoutUsed,
                shortHanded.active,
                shortHanded.remainingSeconds,
                shortHanded.endsAtGameTime
        );
    }

    private static ShortHanded shortHanded(
            List<MatchEvent> active,
            UUID teamId,
            UUID opponentId,
            int elapsedSeconds
    ) {
        int bestEnd = -1;
        for (MatchEvent red : active) {
            if (red.getEventType() != MatchEventType.RED_CARD || !teamId.equals(red.getTeamId())) {
                continue;
            }
            int start = red.getGameTime() == null ? 0 : red.getGameTime();
            int end = start + SHORT_HANDED_SECONDS;
            Integer concedeAt = earliestConcede(active, teamId, opponentId, start, end);
            if (concedeAt != null) {
                end = concedeAt;
            }
            if (elapsedSeconds < end && end > bestEnd) {
                bestEnd = end;
            }
        }
        if (bestEnd < 0) {
            return ShortHanded.none();
        }
        return new ShortHanded(true, bestEnd - elapsedSeconds, bestEnd);
    }

    private static Integer earliestConcede(
            List<MatchEvent> active,
            UUID teamId,
            UUID opponentId,
            int start,
            int end
    ) {
        Integer found = null;
        for (MatchEvent event : active) {
            if (event.getEventType() != MatchEventType.GOAL || event.getGameTime() == null) {
                continue;
            }
            int at = event.getGameTime();
            if (at <= start || at >= end || !benefitsOpponent(event, teamId, opponentId)) {
                continue;
            }
            if (found == null || at < found) {
                found = at;
            }
        }
        return found;
    }

    private static boolean benefitsOpponent(MatchEvent goal, UUID teamId, UUID opponentId) {
        if (isOwnGoal(goal)) {
            return teamId.equals(goal.getTeamId());
        }
        return opponentId.equals(goal.getTeamId());
    }

    private static boolean isOwnGoal(MatchEvent event) {
        return event.getMetadata() != null && Boolean.TRUE.equals(event.getMetadata().get("ownGoal"));
    }

    private static boolean inScope(MatchEvent event, boolean periodScoped, Integer currentPeriod) {
        if (event.isVoided()) {
            return false;
        }
        if (!periodScoped) {
            return true;
        }
        return Objects.equals(currentPeriod, event.getPeriod());
    }

    private record ShortHanded(boolean active, Integer remainingSeconds, Integer endsAtGameTime) {
        private static ShortHanded none() {
            return new ShortHanded(false, null, null);
        }
    }
}
