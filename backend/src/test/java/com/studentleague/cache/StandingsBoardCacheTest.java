package com.studentleague.cache;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.format.TournamentFormatHandler;
import com.studentleague.tournaments.format.TournamentFormatRegistry;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.tournaments.service.TournamentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StandingsBoardCacheTest {

    @Test
    void standingsCalculatorIsNotCalledAgainUntilInvalidated() {
        UUID tournamentId = UUID.randomUUID();
        Tournament tournament = new Tournament();
        tournament.setId(tournamentId);
        tournament.setFormat("ROUND_ROBIN");

        TournamentRepository tournamentRepository = mock(TournamentRepository.class);
        TournamentTeamRepository tournamentTeamRepository = mock(TournamentTeamRepository.class);
        TournamentTableRepository tournamentTableRepository = mock(TournamentTableRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        TournamentFormatRegistry formatRegistry = mock(TournamentFormatRegistry.class);
        TournamentFormatHandler handler = mock(TournamentFormatHandler.class);
        StandingsBoardCache cache = new StandingsBoardCache();

        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentTeamRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(matchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.FINISHED)).thenReturn(List.of());
        when(teamRepository.findAllById(any())).thenReturn(List.of());
        when(tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId)).thenReturn(List.of());
        when(formatRegistry.handler("ROUND_ROBIN")).thenReturn(handler);
        when(handler.standings(any())).thenReturn(List.of());

        TournamentService service = new TournamentService(
                tournamentRepository,
                tournamentTeamRepository,
                tournamentTableRepository,
                mock(SportRepository.class),
                teamRepository,
                mock(PlayerProfileRepository.class),
                matchRepository,
                mock(MatchEventRepository.class),
                mock(MatchLineupPlayerRepository.class),
                mock(MatchRefereeRepository.class),
                mock(NotificationService.class),
                formatRegistry,
                cache
        );

        service.standings(tournamentId);
        clearInvocations(handler, matchRepository);
        service.standings(tournamentId);
        verify(handler, never()).standings(any());
        verify(matchRepository, never()).findByTournamentIdAndStatus(any(), any());

        cache.invalidateTournament(tournamentId);
        service.standings(tournamentId);
        verify(handler, times(1)).standings(any());
        verify(matchRepository, times(1)).findByTournamentIdAndStatus(tournamentId, MatchStatus.FINISHED);
    }
}
