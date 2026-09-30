package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.AvailabilityStatus;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchAvailabilityListResponse;
import com.studentleague.matches.dto.MatchAvailabilityResponse;
import com.studentleague.matches.dto.SetMatchAvailabilityRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchAvailability;
import com.studentleague.matches.repository.MatchAvailabilityRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MatchAvailabilityService {

    private final MatchRepository matchRepository;
    private final MatchAvailabilityRepository availabilityRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamMemberRepository teamMemberRepository;

    public MatchAvailabilityService(
            MatchRepository matchRepository,
            MatchAvailabilityRepository availabilityRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamMemberRepository teamMemberRepository
    ) {
        this.matchRepository = matchRepository;
        this.availabilityRepository = availabilityRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Transactional(readOnly = true)
    public MatchAvailabilityListResponse list(UserPrincipal principal, UUID matchId) {
        Match match = requireMatch(matchId);
        requireRosterPlayer(principal, match);
        List<MatchAvailabilityResponse> going = responses(matchId, AvailabilityStatus.GOING);
        List<MatchAvailabilityResponse> notGoing = responses(matchId, AvailabilityStatus.NOT_GOING);
        return new MatchAvailabilityListResponse(matchId, going.size(), notGoing.size(), going, notGoing);
    }

    @Transactional
    public MatchAvailabilityResponse setOwn(UserPrincipal principal, UUID matchId, SetMatchAvailabilityRequest request) {
        Match match = requireMatch(matchId);
        PlayerProfile self = requireRosterPlayer(principal, match);
        if (request.playerId() != null && !request.playerId().equals(self.getId())) {
            throw ApiException.forbidden("Нельзя отметиться за другого игрока");
        }
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.badRequest("Отметиться можно только на запланированный матч");
        }

        MatchAvailability row = availabilityRepository.findByMatchIdAndPlayerId(matchId, self.getId())
                .orElseGet(MatchAvailability::new);
        row.setMatchId(matchId);
        row.setPlayerId(self.getId());
        row.setStatus(request.status());
        MatchAvailability saved = availabilityRepository.save(row);
        return toResponse(saved, self);
    }

    private List<MatchAvailabilityResponse> responses(UUID matchId, AvailabilityStatus status) {
        return availabilityRepository.findByMatchId(matchId).stream()
                .filter(row -> row.getStatus() == status)
                .map(row -> toResponse(row, playerProfileRepository.findById(row.getPlayerId()).orElse(null)))
                .sorted(Comparator.comparing(
                        MatchAvailabilityResponse::displayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ))
                .toList();
    }

    private MatchAvailabilityResponse toResponse(MatchAvailability row, PlayerProfile profile) {
        return new MatchAvailabilityResponse(
                row.getId(),
                row.getMatchId(),
                row.getPlayerId(),
                MatchMapper.displayName(profile),
                row.getStatus(),
                row.getUpdatedAt()
        );
    }

    private PlayerProfile requireRosterPlayer(UserPrincipal principal, Match match) {
        if (principal == null) {
            throw ApiException.unauthorized("Нужно войти");
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Отметиться могут только игроки команд этого матча"));
        boolean onHome = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getHomeTeamId(), profile.getId(), TeamMemberStatus.ACTIVE);
        boolean onAway = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getAwayTeamId(), profile.getId(), TeamMemberStatus.ACTIVE);
        if (!onHome && !onAway) {
            throw ApiException.forbidden("Отметиться могут только игроки команд этого матча");
        }
        return profile;
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
    }
}
