package com.studentleague.matches.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.scoring.ScorePolicyRegistry;
import com.studentleague.matches.scoring.ScoreSnapshot;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchScoreService {

    private final MatchEventRepository matchEventRepository;
    private final SportRepository sportRepository;
    private final ScorePolicyRegistry scorePolicyRegistry;
    private final StandingsBoardCache standingsBoardCache;

    public MatchScoreService(
            MatchEventRepository matchEventRepository,
            SportRepository sportRepository,
            ScorePolicyRegistry scorePolicyRegistry,
            StandingsBoardCache standingsBoardCache
    ) {
        this.matchEventRepository = matchEventRepository;
        this.sportRepository = sportRepository;
        this.scorePolicyRegistry = scorePolicyRegistry;
        this.standingsBoardCache = standingsBoardCache;
    }

    public void apply(Match match) {
        Sport sport = sportRepository.findById(match.getSportId())
                .orElseThrow(() -> ApiException.notFound("Sport not found"));
        List<MatchEvent> active = matchEventRepository.findByMatchIdAndVoidedFalseOrderByTimestampAsc(match.getId());
        ScoreSnapshot snapshot = scorePolicyRegistry.forSportCode(sport.getCode())
                .calculate(match.getHomeTeamId(), match.getAwayTeamId(), active);
        match.setHomeScore(snapshot.homeScore());
        match.setAwayScore(snapshot.awayScore());
        standingsBoardCache.invalidateTournament(match.getTournamentId());
    }
}
