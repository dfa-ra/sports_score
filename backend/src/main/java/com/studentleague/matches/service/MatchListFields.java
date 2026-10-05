package com.studentleague.matches.service;

import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.players.entity.PlayerProfile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatchListFields {

    private MatchListFields() {
    }

    public static Integer minute(Match match, Instant now) {
        if (match == null) {
            return null;
        }
        if (match.getStatus() != MatchStatus.LIVE && match.getStatus() != MatchStatus.PAUSED) {
            return null;
        }
        if (match.getPeriod() == null && match.getGameTimeSeconds() == null && match.getClockRunningSince() == null) {
            return null;
        }
        return MatchClock.elapsedSeconds(match, now) / 60;
    }

    public static Map<UUID, String> lastGoalScorers(List<MatchEvent> goalsNewestFirst, Map<UUID, PlayerProfile> players) {
        Map<UUID, String> names = new HashMap<>();
        if (goalsNewestFirst == null) {
            return names;
        }
        for (MatchEvent goal : goalsNewestFirst) {
            if (goal.getMatchId() == null || names.containsKey(goal.getMatchId())) {
                continue;
            }
            PlayerProfile player = goal.getPlayerId() == null || players == null
                    ? null
                    : players.get(goal.getPlayerId());
            names.put(goal.getMatchId(), MatchMapper.registeredName(player));
        }
        return names;
    }
}
