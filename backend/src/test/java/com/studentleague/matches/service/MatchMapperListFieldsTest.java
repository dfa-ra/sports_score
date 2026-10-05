package com.studentleague.matches.service;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.repository.SportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchMapperListFieldsTest {

    @Mock
    SportRepository sportRepository;

    @Mock
    PlayerProfileRepository playerProfileRepository;

    @Mock
    MatchEventRepository matchEventRepository;

    @Test
    void responseCarriesLastGoalScorerAndMinute() {
        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        PlayerProfile player = new PlayerProfile();
        player.setId(playerId);
        player.setFirstName("Анна");
        player.setLastName("Соколова");
        player.setDisplayName("Anya");

        MatchEvent goal = new MatchEvent();
        goal.setId(UUID.randomUUID());
        goal.setMatchId(matchId);
        goal.setPlayerId(playerId);
        goal.setEventType(MatchEventType.GOAL);
        goal.setTimestamp(Instant.parse("2026-09-30T12:08:00Z"));
        goal.setVoided(false);

        when(sportRepository.findById(any())).thenReturn(Optional.empty());
        when(matchEventRepository.findActiveByMatchIdsAndType(any(), eq(MatchEventType.GOAL)))
                .thenReturn(List.of(goal));
        when(playerProfileRepository.findAllById(any())).thenReturn(List.of(player));

        Match match = new Match();
        match.setId(matchId);
        match.setTournamentId(UUID.randomUUID());
        match.setSportId(UUID.randomUUID());
        match.setHomeTeamId(UUID.randomUUID());
        match.setAwayTeamId(UUID.randomUUID());
        match.setScheduledAt(Instant.parse("2026-09-30T12:00:00Z"));
        match.setStatus(MatchStatus.LIVE);
        match.setHomeScore(1);
        match.setAwayScore(0);
        match.setPeriod(1);
        match.setGameTimeSeconds(180);
        match.setPeriodCount(2);
        match.setPeriodLengthSeconds(20 * 60);

        MatchMapper mapper = new MatchMapper(sportRepository, playerProfileRepository, matchEventRepository);
        MatchResponse response = mapper.toResponse(match);

        assertThat(response.lastGoalScorer()).isEqualTo("Соколова Анна");
        assertThat(response.minute()).isEqualTo(3);
        assertThat(response.homeScore()).isEqualTo(1);
    }
}
