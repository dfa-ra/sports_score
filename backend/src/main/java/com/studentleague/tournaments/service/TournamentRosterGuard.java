package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.service.MatchMapper;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A player may be active on several live clubs. Inside one tournament they
 * play for one club. {@code teams} has no tournament id, so the rule follows
 * {@link TournamentTeam} entries. A disbanded club does not hold the player.
 * Pending and approved entries count as entered; withdrawn and rejected do not.
 */
@Service
public class TournamentRosterGuard {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public TournamentRosterGuard(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentRepository tournamentRepository,
            PlayerProfileRepository playerProfileRepository
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentRepository = tournamentRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    public static boolean isEntered(TournamentTeamStatus status) {
        return status == TournamentTeamStatus.PENDING || status == TournamentTeamStatus.APPROVED;
    }

    /**
     * 409 when {@code teamId} is entered in a tournament where {@code playerId}
     * is already active on a different live club that is entered there too.
     * A club with no entry does not conflict, even if the player is active elsewhere.
     */
    public void rejectSecondClubInSharedTournament(UUID playerId, UUID teamId) {
        Conflict chosen = null;
        for (TournamentTeam entry : tournamentTeamRepository.findByTeamId(teamId)) {
            if (!isEntered(entry.getStatus())) {
                continue;
            }
            chosen = earlier(chosen, conflictIn(entry.getTournamentId(), teamId, playerId));
        }
        throwIfPresent(chosen);
    }

    /**
     * 409 when an active member of {@code teamId} is already active on another
     * live club entered in {@code tournamentId}. The message names those players
     * and the clubs they already play for.
     */
    public void rejectSquadAlreadyInTournament(UUID tournamentId, UUID teamId) {
        List<TeamMember> squad = teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE);
        if (squad.isEmpty()) {
            return;
        }
        List<UUID> playerIds = squad.stream().map(TeamMember::getPlayerId).distinct().toList();
        Map<UUID, PlayerProfile> profiles = playerProfileRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, profile -> profile, (left, right) -> left));
        List<String> clauses = new ArrayList<>();
        for (UUID playerId : playerIds) {
            List<String> clubs = otherClubNames(tournamentId, teamId, playerId);
            if (clubs.isEmpty()) {
                continue;
            }
            String quoted = clubs.stream().map(name -> "«" + name + "»").collect(Collectors.joining(", "));
            clauses.add(playerLabel(profiles.get(playerId)) + " уже играет за " + quoted);
        }
        if (clauses.isEmpty()) {
            return;
        }
        clauses.sort(Comparator.naturalOrder());
        throw ApiException.conflict(String.join("; ", clauses));
    }

    /**
     * Match lineup, protocol and goalkeeper picks. No-op when the match is not
     * in a tournament, so a player on two clubs in different tournaments can
     * still appear for each club in its own tournament.
     */
    public void rejectAppearanceForSecondClub(UUID tournamentId, UUID teamId, UUID playerId) {
        if (tournamentId == null) {
            return;
        }
        throwIfPresent(conflictIn(tournamentId, teamId, playerId));
    }

    private Conflict conflictIn(UUID tournamentId, UUID teamId, UUID playerId) {
        List<String> others = otherClubNames(tournamentId, teamId, playerId);
        if (others.isEmpty()) {
            return null;
        }
        Tournament tournament = tournamentRepository.findById(tournamentId).orElse(null);
        if (tournament == null || tournament.getName() == null || tournament.getName().isBlank()) {
            return null;
        }
        return new Conflict(others.getFirst(), tournament.getName().trim());
    }

    private List<String> otherClubNames(UUID tournamentId, UUID teamId, UUID playerId) {
        List<String> names = new ArrayList<>();
        for (TeamMember membership : teamMemberRepository.findByPlayerIdAndStatus(playerId, TeamMemberStatus.ACTIVE)) {
            if (membership.getTeamId() == null || membership.getTeamId().equals(teamId)) {
                continue;
            }
            Team other = teamRepository.findById(membership.getTeamId()).orElse(null);
            if (other == null || other.isDisbanded() || other.getName() == null || other.getName().isBlank()) {
                continue;
            }
            if (!enteredIn(tournamentId, other.getId())) {
                continue;
            }
            names.add(other.getName().trim());
        }
        return names.stream().distinct().sorted().toList();
    }

    private boolean enteredIn(UUID tournamentId, UUID teamId) {
        return tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .map(entry -> isEntered(entry.getStatus()))
                .orElse(false);
    }

    private static void throwIfPresent(Conflict conflict) {
        if (conflict == null) {
            return;
        }
        throw ApiException.conflict(
                "Игрок уже играет за «" + conflict.teamName + "» в турнире «" + conflict.tournamentName + "»"
        );
    }

    private static Conflict earlier(Conflict left, Conflict right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        int byTournament = left.tournamentName.compareTo(right.tournamentName);
        if (byTournament != 0) {
            return byTournament < 0 ? left : right;
        }
        return left.teamName.compareTo(right.teamName) <= 0 ? left : right;
    }

    private static String playerLabel(PlayerProfile profile) {
        String name = MatchMapper.registeredName(profile);
        if (name == null || name.isBlank()) {
            return "Игрок";
        }
        return name;
    }

    private record Conflict(String teamName, String tournamentName) {
    }
}
