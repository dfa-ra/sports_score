package com.studentleague.statistics;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    MatchEventRepository matchEventRepository;

    @Mock
    com.studentleague.matches.repository.MatchRepository matchRepository;

    @Mock
    com.studentleague.tournaments.repository.TournamentRepository tournamentRepository;

    @Mock
    com.studentleague.players.repository.PlayerProfileRepository playerProfileRepository;

    @Mock
    com.studentleague.teams.repository.TeamRepository teamRepository;

    @Mock
    MatchLineupPlayerRepository lineupPlayerRepository;

    @InjectMocks
    StatisticsService statisticsService;

    @Test
    void aggregatesGoalsAndIgnoresVoidedViaRepositoryFilter() {
        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        com.studentleague.matches.entity.Match match = new com.studentleague.matches.entity.Match();
        match.setId(matchId);
        match.setHomeTeamId(teamId);
        match.setAwayTeamId(UUID.randomUUID());
        match.setTournamentId(UUID.randomUUID());

        MatchEvent goal = new MatchEvent();
        goal.setMatchId(matchId);
        goal.setEventType(MatchEventType.GOAL);
        goal.setPlayerId(playerId);
        goal.setTeamId(teamId);
        goal.setVoided(false);

        MatchEvent assist = new MatchEvent();
        assist.setMatchId(matchId);
        assist.setEventType(MatchEventType.ASSIST);
        assist.setPlayerId(playerId);
        assist.setTeamId(teamId);
        assist.setVoided(false);

        when(matchRepository.findAll()).thenReturn(List.of(match));
        when(matchEventRepository.findByMatchIdInAndVoidedFalse(any()))
                .thenReturn(List.of(goal, assist));
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of());

        List<PlayerStatisticsResponse> stats = statisticsService.playerStatistics(null, null, null, playerId);

        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().goals()).isEqualTo(1);
        assertThat(stats.getFirst().assists()).isEqualTo(1);
        assertThat(stats.getFirst().appearances()).isEqualTo(1);
    }

    @Test
    void finishedShutoutCountsOneCleanSheetAndAConcededMatchDoesNot() {
        UUID tournamentId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        UUID keeperId = UUID.randomUUID();
        UUID forwardId = UUID.randomUUID();
        UUID shutoutId = UUID.randomUUID();
        UUID concededId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();

        Match shutout = finished(shutoutId, tournamentId, homeId, awayId, 2, 0);
        Match conceded = finished(concededId, tournamentId, homeId, awayId, 1, 2);
        Match live = finished(liveId, tournamentId, homeId, awayId, 4, 0);
        live.setStatus(MatchStatus.LIVE);

        when(matchRepository.findByTournamentId(tournamentId)).thenReturn(List.of(shutout, conceded, live));
        when(lineupPlayerRepository.findByMatchIdIn(any())).thenReturn(List.of(
                lineup(shutoutId, homeId, keeperId),
                lineup(shutoutId, homeId, forwardId),
                lineup(concededId, homeId, keeperId),
                lineup(concededId, homeId, forwardId),
                lineup(liveId, homeId, keeperId)
        ));
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of(
                profile(keeperId, "Иван Вратарёв", "Вратарь"),
                profile(forwardId, "Пётр Нападающий", "Нападающий")
        ));

        MatchEvent card = new MatchEvent();
        card.setMatchId(concededId);
        card.setEventType(MatchEventType.YELLOW_CARD);
        card.setPlayerId(keeperId);
        card.setTeamId(homeId);
        card.setVoided(false);
        when(matchEventRepository.findByMatchIdInAndVoidedFalse(any())).thenReturn(List.of(card));

        List<PlayerStatisticsResponse> rows = statisticsService.goalkeepers(tournamentId, 10);

        assertThat(rows).extracting(PlayerStatisticsResponse::playerId).containsExactly(keeperId);
        assertThat(rows.getFirst().displayName()).isEqualTo("Иван Вратарёв");
        assertThat(rows.getFirst().cleanSheets()).isEqualTo(1);
        assertThat(rows.getFirst().appearances()).isEqualTo(2);
    }

    private static Match finished(UUID id, UUID tournamentId, UUID homeId, UUID awayId, int homeScore, int awayScore) {
        Match match = new Match();
        match.setId(id);
        match.setTournamentId(tournamentId);
        match.setHomeTeamId(homeId);
        match.setAwayTeamId(awayId);
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }

    private static MatchLineupPlayer lineup(UUID matchId, UUID teamId, UUID playerId) {
        MatchLineupPlayer row = new MatchLineupPlayer();
        row.setMatchId(matchId);
        row.setTeamId(teamId);
        row.setPlayerId(playerId);
        row.setStarter(true);
        return row;
    }

    private static PlayerProfile profile(UUID id, String name, String position) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setDisplayName(name);
        profile.setPosition(position);
        return profile;
    }
}
