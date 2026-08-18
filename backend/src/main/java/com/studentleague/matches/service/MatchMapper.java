package com.studentleague.matches.service;

import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import org.springframework.stereotype.Component;

@Component
public class MatchMapper {

    private final SportRepository sportRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public MatchMapper(SportRepository sportRepository, PlayerProfileRepository playerProfileRepository) {
        this.sportRepository = sportRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    public MatchResponse toResponse(Match match) {
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
                sportCode
        );
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
                event.getCreatedAt()
        );
    }

    public static String displayName(PlayerProfile player) {
        if (player == null) {
            return null;
        }
        if (player.getDisplayName() != null && !player.getDisplayName().isBlank()) {
            return player.getDisplayName();
        }
        return (player.getFirstName() + " " + player.getLastName()).trim();
    }
}
