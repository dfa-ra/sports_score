package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlayerOfTheMatchService {

    private final MatchRepository matchRepository;
    private final MatchLineupPlayerRepository lineupRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MatchMapper matchMapper;

    public PlayerOfTheMatchService(
            MatchRepository matchRepository,
            MatchLineupPlayerRepository lineupRepository,
            TeamMemberRepository teamMemberRepository,
            MatchMapper matchMapper
    ) {
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.matchMapper = matchMapper;
    }

    @Transactional
    public MatchResponse set(UUID matchId, UUID playerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        if (match.getStatus() != MatchStatus.FINISHED) {
            throw ApiException.badRequest("Игрока матча выбирают после финального свистка");
        }
        if (playerId != null && !eligible(match, playerId)) {
            throw ApiException.badRequest("Игрок матча должен быть в составе одной из команд");
        }
        match.setPlayerOfTheMatchId(playerId);
        return matchMapper.toResponse(matchRepository.save(match));
    }

    /**
     * A submitted lineup limits the pick to those players.
     * A side that never sent a lineup falls back to its active squad,
     * so a finished match can still get a player of the match.
     */
    private boolean eligible(Match match, UUID playerId) {
        return onSide(match.getId(), match.getHomeTeamId(), playerId)
                || onSide(match.getId(), match.getAwayTeamId(), playerId);
    }

    private boolean onSide(UUID matchId, UUID teamId, UUID playerId) {
        if (lineupRepository.existsByMatchIdAndTeamId(matchId, teamId)) {
            return lineupRepository.findByMatchIdAndTeamId(matchId, teamId).stream()
                    .map(MatchLineupPlayer::getPlayerId)
                    .anyMatch(playerId::equals);
        }
        return teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                teamId, playerId, TeamMemberStatus.ACTIVE);
    }
}
