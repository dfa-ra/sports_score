package com.studentleague.statistics;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
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
        when(matchEventRepository.findByMatchIdAndVoidedFalseOrderByTimestampAsc(matchId))
                .thenReturn(List.of(goal, assist));
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of());

        List<PlayerStatisticsResponse> stats = statisticsService.playerStatistics(null, null, null, playerId);

        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().goals()).isEqualTo(1);
        assertThat(stats.getFirst().assists()).isEqualTo(1);
        assertThat(stats.getFirst().appearances()).isEqualTo(1);
    }
}
