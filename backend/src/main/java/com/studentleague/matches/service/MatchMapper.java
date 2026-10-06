package com.studentleague.matches.service;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.dto.PlayerOfTheMatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MatchMapper {

    private final SportRepository sportRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchEventRepository matchEventRepository;

    public MatchMapper(
            SportRepository sportRepository,
            PlayerProfileRepository playerProfileRepository,
            MatchEventRepository matchEventRepository
    ) {
        this.sportRepository = sportRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.matchEventRepository = matchEventRepository;
    }

    public MatchResponse toResponse(Match match) {
        return toResponse(match, lastGoalScorer(match.getId()));
    }

    public Page<MatchResponse> toPage(Page<Match> page) {
        Map<UUID, String> scorers = lastGoalScorers(page.getContent().stream().map(Match::getId).toList());
        return page.map(match -> toResponse(match, scorers.get(match.getId())));
    }

    public List<MatchResponse> toList(List<Match> matches) {
        Map<UUID, String> scorers = lastGoalScorers(matches.stream().map(Match::getId).toList());
        return matches.stream().map(match -> toResponse(match, scorers.get(match.getId()))).toList();
    }

    public MatchResponse toResponse(Match match, String lastGoalScorer) {
        String sportCode = sportRepository.findById(match.getSportId())
                .map(Sport::getCode)
                .orElse(null);
        return new MatchResponse(
                match.getId(),
                match.getTournamentId(),
                match.getSportId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getScheduledAt(),
                match.getStartedAt(),
                match.getFinishedAt(),
                match.getStatus(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getGameTimeSeconds(),
                match.getPeriod(),
                match.getPeriodCount(),
                match.getPeriodLengthSeconds(),
                match.getClockRunningSince(),
                sportCode,
                match.getVenue(),
                lastGoalScorer,
                MatchListFields.minute(match, Instant.now()),
                playerOfTheMatch(match)
        );
    }

    private PlayerOfTheMatchResponse playerOfTheMatch(Match match) {
        UUID playerId = match.getPlayerOfTheMatchId();
        if (playerId == null) {
            return null;
        }
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElse(null);
        if (profile == null) {
            return new PlayerOfTheMatchResponse(playerId, null, null);
        }
        return new PlayerOfTheMatchResponse(playerId, profile.getFirstName(), profile.getLastName());
    }

    private String lastGoalScorer(UUID matchId) {
        if (matchId == null) {
            return null;
        }
        return lastGoalScorers(List.of(matchId)).get(matchId);
    }

    private Map<UUID, String> lastGoalScorers(List<UUID> matchIds) {
        List<UUID> ids = matchIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MatchEvent> goals = matchEventRepository.findActiveByMatchIdsAndType(ids, MatchEventType.GOAL);
        Map<UUID, PlayerProfile> players = new HashMap<>();
        List<UUID> playerIds = goals.stream().map(MatchEvent::getPlayerId).filter(id -> id != null).distinct().toList();
        if (!playerIds.isEmpty()) {
            for (PlayerProfile profile : playerProfileRepository.findAllById(playerIds)) {
                players.put(profile.getId(), profile);
            }
        }
        return MatchListFields.lastGoalScorers(goals, players);
    }

    public MatchEventResponse toEventResponse(MatchEvent event) {
        PlayerProfile player = event.getPlayerId() == null
                ? null
                : playerProfileRepository.findById(event.getPlayerId()).orElse(null);
        PlayerProfile secondary = event.getSecondaryPlayerId() == null
                ? null
                : playerProfileRepository.findById(event.getSecondaryPlayerId()).orElse(null);
        return new MatchEventResponse(
                event.getId(),
                event.getMatchId(),
                event.getEventType(),
                event.getTimestamp(),
                event.getGameTime(),
                event.getPeriod(),
                event.getTeamId(),
                event.getPlayerId(),
                displayName(player),
                player == null ? null : player.getJerseyNumber(),
                event.getSecondaryPlayerId(),
                displayName(secondary),
                secondary == null ? null : secondary.getJerseyNumber(),
                event.getMetadata(),
                event.isVoided(),
                event.getVoidedAt(),
                event.getCreatedAt(),
                player == null ? null : player.getFirstName(),
                player == null ? null : player.getLastName(),
                secondary == null ? null : secondary.getFirstName(),
                secondary == null ? null : secondary.getLastName()
        );
    }

    /** Shirt nickname, kept for clients that still read it. Not the name shown in lineups. */
    public static String displayName(PlayerProfile player) {
        if (player == null) {
            return null;
        }
        if (player.getDisplayName() != null && !player.getDisplayName().isBlank()) {
            return player.getDisplayName();
        }
        return (player.getFirstName() + " " + player.getLastName()).trim();
    }

    /** Registration surname and given name. Shirt nickname is only a fallback when both are blank. */
    public static String registeredName(PlayerProfile player) {
        if (player == null) {
            return null;
        }
        String last = player.getLastName() == null ? "" : player.getLastName().trim();
        String first = player.getFirstName() == null ? "" : player.getFirstName().trim();
        String legal = (last + " " + first).trim();
        if (!legal.isEmpty()) {
            return legal;
        }
        return displayName(player);
    }
}
