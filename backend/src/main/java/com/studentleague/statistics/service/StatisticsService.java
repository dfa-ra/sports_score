package com.studentleague.statistics.service;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.dto.TeamStatisticsResponse;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final MatchEventRepository matchEventRepository;
    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRepository teamRepository;

    public StatisticsService(
            MatchEventRepository matchEventRepository,
            MatchRepository matchRepository,
            TournamentRepository tournamentRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamRepository teamRepository
    ) {
        this.matchEventRepository = matchEventRepository;
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerStatisticsResponse> playerStatistics(
            UUID tournamentId,
            Integer seasonYear,
            UUID teamId,
            UUID playerId
    ) {
        Set<UUID> matchIds = resolveMatchIds(tournamentId, seasonYear, teamId);
        Map<UUID, PlayerAccumulator> stats = new HashMap<>();

        for (UUID matchId : matchIds) {
            List<MatchEvent> events = matchEventRepository.findByMatchIdAndVoidedFalseOrderByTimestampAsc(matchId);
            Set<UUID> appeared = new HashSet<>();
            for (MatchEvent event : events) {
                if (event.getPlayerId() == null) {
                    continue;
                }
                if (playerId != null && !playerId.equals(event.getPlayerId())) {
                    continue;
                }
                if (teamId != null && event.getTeamId() != null && !teamId.equals(event.getTeamId())) {
                    continue;
                }
                PlayerAccumulator acc = stats.computeIfAbsent(event.getPlayerId(), PlayerAccumulator::new);
                appeared.add(event.getPlayerId());
                switch (event.getEventType()) {
                    case GOAL -> acc.goals++;
                    case ASSIST -> acc.assists++;
                    case YELLOW_CARD -> acc.yellowCards++;
                    case RED_CARD -> acc.redCards++;
                    default -> {
                    }
                }
                if (event.getTeamId() != null) {
                    acc.teamId = event.getTeamId();
                }
            }
            for (UUID appearedPlayerId : appeared) {
                if (playerId != null && !playerId.equals(appearedPlayerId)) {
                    continue;
                }
                stats.computeIfAbsent(appearedPlayerId, PlayerAccumulator::new).appearances++;
            }
        }

        Map<UUID, PlayerProfile> profiles = playerProfileRepository.findAllById(stats.keySet()).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, p -> p));

        return stats.values().stream()
                .map(acc -> {
                    PlayerProfile profile = profiles.get(acc.playerId);
                    return new PlayerStatisticsResponse(
                            acc.playerId,
                            profile == null ? null : profile.getDisplayName(),
                            acc.goals,
                            acc.assists,
                            acc.yellowCards,
                            acc.redCards,
                            acc.appearances,
                            acc.teamId
                    );
                })
                .sorted((a, b) -> Long.compare(b.goals(), a.goals()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamStatisticsResponse> teamStatistics(UUID tournamentId, Integer seasonYear, UUID teamId) {
        Set<UUID> matchIds = resolveMatchIds(tournamentId, seasonYear, teamId);
        Map<UUID, TeamAccumulator> table = new HashMap<>();

        for (UUID matchId : matchIds) {
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null || match.getStatus() != MatchStatus.FINISHED) {
                continue;
            }
            if (teamId != null
                    && !teamId.equals(match.getHomeTeamId())
                    && !teamId.equals(match.getAwayTeamId())) {
                continue;
            }
            TeamAccumulator home = table.computeIfAbsent(match.getHomeTeamId(), TeamAccumulator::new);
            TeamAccumulator away = table.computeIfAbsent(match.getAwayTeamId(), TeamAccumulator::new);
            home.goalsFor += match.getHomeScore();
            home.goalsAgainst += match.getAwayScore();
            away.goalsFor += match.getAwayScore();
            away.goalsAgainst += match.getHomeScore();
            if (match.getHomeScore() > match.getAwayScore()) {
                home.wins++;
                home.points += 3;
                away.losses++;
            } else if (match.getHomeScore() < match.getAwayScore()) {
                away.wins++;
                away.points += 3;
                home.losses++;
            } else {
                home.draws++;
                away.draws++;
                home.points++;
                away.points++;
            }
        }

        Map<UUID, Team> teams = teamRepository.findAllById(table.keySet()).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        return table.values().stream()
                .map(acc -> new TeamStatisticsResponse(
                        acc.teamId,
                        teams.containsKey(acc.teamId) ? teams.get(acc.teamId).getName() : null,
                        acc.wins,
                        acc.draws,
                        acc.losses,
                        acc.points,
                        acc.goalsFor,
                        acc.goalsAgainst
                ))
                .sorted((a, b) -> Long.compare(b.points(), a.points()))
                .toList();
    }

    private Set<UUID> resolveMatchIds(UUID tournamentId, Integer seasonYear, UUID teamId) {
        List<Match> matches;
        if (tournamentId != null) {
            matches = matchRepository.findByTournamentId(tournamentId, Pageable.unpaged()).getContent();
        } else if (seasonYear != null) {
            Set<UUID> tournamentIds = tournamentRepository.findAll().stream()
                    .filter(t -> seasonYear.equals(t.getSeasonYear()))
                    .map(Tournament::getId)
                    .collect(Collectors.toSet());
            matches = matchRepository.findAll().stream()
                    .filter(m -> tournamentIds.contains(m.getTournamentId()))
                    .toList();
        } else {
            matches = matchRepository.findAll();
        }
        return matches.stream()
                .filter(m -> teamId == null
                        || teamId.equals(m.getHomeTeamId())
                        || teamId.equals(m.getAwayTeamId()))
                .map(Match::getId)
                .collect(Collectors.toSet());
    }

    private static final class PlayerAccumulator {
        private final UUID playerId;
        private long goals;
        private long assists;
        private long yellowCards;
        private long redCards;
        private long appearances;
        private UUID teamId;

        private PlayerAccumulator(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private static final class TeamAccumulator {
        private final UUID teamId;
        private long wins;
        private long draws;
        private long losses;
        private long points;
        private long goalsFor;
        private long goalsAgainst;

        private TeamAccumulator(UUID teamId) {
            this.teamId = teamId;
        }
    }
}
