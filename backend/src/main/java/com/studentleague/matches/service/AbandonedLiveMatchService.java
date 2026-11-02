package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A match stays LIVE until the referee presses finish. If nobody has written
 * an event for hours, the row is abandoned, not a match being played now.
 */
@Service
public class AbandonedLiveMatchService {

    /** Longer than a student match, a break, and a quiet spell without a goal. */
    static final Duration QUIET = Duration.ofHours(6);

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchScoreService matchScoreService;

    public AbandonedLiveMatchService(
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchScoreService matchScoreService
    ) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchScoreService = matchScoreService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeIdle(Instant now) {
        List<Match> live = matchRepository.findByStatusIn(List.of(MatchStatus.LIVE, MatchStatus.PAUSED));
        for (Match match : live) {
            if (!idleSince(lastActivity(match), now)) {
                continue;
            }
            close(match, now);
        }
    }

    static boolean idleSince(Instant activity, Instant now) {
        if (activity == null || now == null) {
            return false;
        }
        return !activity.plus(QUIET).isAfter(now);
    }

    private Instant lastActivity(Match match) {
        Instant activity = match.getStartedAt() != null ? match.getStartedAt() : match.getScheduledAt();
        Optional<MatchEvent> latest = matchEventRepository.findFirstByMatchIdOrderByTimestampDesc(match.getId());
        if (latest.isEmpty()) {
            return activity;
        }
        Instant stamp = latest.get().getTimestamp();
        if (stamp != null && (activity == null || stamp.isAfter(activity))) {
            return stamp;
        }
        return activity;
    }

    private void close(Match match, Instant now) {
        try {
            matchScoreService.apply(match);
        } catch (ApiException ex) {
            return;
        }
        MatchClock.freeze(match, now);
        match.setStatus(MatchStatus.FINISHED);
        if (match.getFinishedAt() == null) {
            match.setFinishedAt(now);
        }
        matchRepository.save(match);
    }
}
