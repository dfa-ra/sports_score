package com.studentleague.statistics;

import com.studentleague.cache.StandingsBoardCache;
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
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    TeamRepository teamRepository;

    @Mock
    MatchLineupPlayerRepository lineupPlayerRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    UserRepository userRepository;

    @Spy
    StandingsBoardCache standingsBoardCache = new StandingsBoardCache();

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
    void boardIsServedFromCacheUntilTheTournamentIsInvalidated() {
        UUID tournamentId = UUID.randomUUID();
        when(matchRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of());

        statisticsService.board(tournamentId, 20);
        clearInvocations(matchRepository);
        statisticsService.board(tournamentId, 20);
        verify(matchRepository, never()).findByTournamentId(any());

        standingsBoardCache.invalidateTournament(tournamentId);
        statisticsService.board(tournamentId, 20);
        verify(matchRepository, times(2)).findByTournamentId(tournamentId);

        clearInvocations(matchRepository);
        statisticsService.board(tournamentId, 5);
        verify(matchRepository, times(2)).findByTournamentId(tournamentId);
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
        assertThat(rows.getFirst().firstName()).isEqualTo("Иван");
        assertThat(rows.getFirst().lastName()).isEqualTo("Вратарёв");
        assertThat(rows.getFirst().cleanSheets()).isEqualTo(1);
        assertThat(rows.getFirst().appearances()).isEqualTo(2);
    }

    @Test
    void scorerPayloadIncludesAvatarAndTeamCrestWhenTheyExist() {
        UUID tournamentId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID scorerId = UUID.randomUUID();
        UUID plainId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Match match = finished(matchId, tournamentId, teamId, UUID.randomUUID(), 2, 0);

        MatchEvent goal = new MatchEvent();
        goal.setMatchId(matchId);
        goal.setEventType(MatchEventType.GOAL);
        goal.setPlayerId(scorerId);
        goal.setTeamId(teamId);

        MatchEvent otherGoal = new MatchEvent();
        otherGoal.setMatchId(matchId);
        otherGoal.setEventType(MatchEventType.GOAL);
        otherGoal.setPlayerId(plainId);

        PlayerProfile scorer = named(scorerId, "Алексей", "Смирнов");
        scorer.setAvatarUrl("/media/avatars/smirnov.png");
        PlayerProfile plain = named(plainId, "Павел", "Безфото");
        plain.setUserId(userId);
        plain.setAvatarUrl("  ");

        Team team = new Team();
        team.setId(teamId);
        team.setName("Кронбарсы");
        team.setLogoUrl("/media/crests/kronbars.png");

        TeamMember member = new TeamMember();
        member.setPlayerId(scorerId);
        member.setTeamId(teamId);
        member.setStatus(TeamMemberStatus.ACTIVE);

        User user = new User();
        user.setId(userId);
        user.setPhotoUrl("https://cdn.example/pavel.jpg");

        when(matchRepository.findByTournamentId(tournamentId)).thenReturn(List.of(match));
        when(matchEventRepository.findByMatchIdInAndVoidedFalse(any())).thenReturn(List.of(goal, otherGoal));
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of(scorer, plain));
        when(teamMemberRepository.findByPlayerIdInAndStatus(any(), eq(TeamMemberStatus.ACTIVE))).thenReturn(List.of(member));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        List<PlayerStatisticsResponse> rows = statisticsService.scorers(tournamentId, 10);

        PlayerStatisticsResponse withMedia = rows.stream()
                .filter(row -> scorerId.equals(row.playerId()))
                .findFirst()
                .orElseThrow();
        assertThat(withMedia.avatarUrl()).isEqualTo("/media/avatars/smirnov.png");
        assertThat(withMedia.teamId()).isEqualTo(teamId);
        assertThat(withMedia.teamLogoUrl()).isEqualTo("/media/crests/kronbars.png");
        assertThat(withMedia.lastName()).isEqualTo("Смирнов");
        assertThat(withMedia.firstName()).isEqualTo("Алексей");

        PlayerStatisticsResponse withoutTeam = rows.stream()
                .filter(row -> plainId.equals(row.playerId()))
                .findFirst()
                .orElseThrow();
        assertThat(withoutTeam.avatarUrl()).isEqualTo("https://cdn.example/pavel.jpg");
        assertThat(withoutTeam.teamId()).isNull();
        assertThat(withoutTeam.teamLogoUrl()).isNull();

        verify(playerProfileRepository, times(1)).findAllById(any());
        verify(teamMemberRepository, times(1)).findByPlayerIdInAndStatus(any(), eq(TeamMemberStatus.ACTIVE));
        verify(teamRepository, times(1)).findAllById(any());
        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(any());
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
        profile.setFirstName("Иван");
        profile.setLastName("Вратарёв");
        profile.setPosition(position);
        return profile;
    }

    private static PlayerProfile named(UUID id, String firstName, String lastName) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setDisplayName(lastName + " " + firstName);
        return profile;
    }
}
