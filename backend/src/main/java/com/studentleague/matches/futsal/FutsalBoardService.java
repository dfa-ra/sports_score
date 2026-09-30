package com.studentleague.matches.futsal;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.dto.FutsalBoardResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FutsalBoardService {

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;

    public FutsalBoardService(MatchRepository matchRepository, MatchEventRepository matchEventRepository) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
    }

    @Transactional(readOnly = true)
    public FutsalBoardResponse forMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        int elapsed = MatchClock.elapsedSeconds(match, Instant.now());
        return FutsalBoardCalculator.build(
                match,
                matchEventRepository.findByMatchIdOrderByTimestampAsc(matchId),
                elapsed
        );
    }
}
