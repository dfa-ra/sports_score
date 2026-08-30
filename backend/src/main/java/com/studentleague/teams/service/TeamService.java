package com.studentleague.teams.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.notifications.NotificationService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.dto.AddTeamMemberRequest;
import com.studentleague.teams.dto.AssignCaptainRequest;
import com.studentleague.teams.dto.CreateTeamRequest;
import com.studentleague.teams.dto.TeamMemberResponse;
import com.studentleague.teams.dto.TeamResponse;
import com.studentleague.teams.dto.UpdateTeamRequest;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final NotificationService notificationService;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentTeamRepository tournamentTeamRepository,
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository,
            RoleService roleService,
            NotificationService notificationService
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TeamResponse createTeam(UserPrincipal principal, CreateTeamRequest request) {
        if (!principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Команду создаёт только админ");
        }

        Team team = new Team();
        team.setName(request.name().trim());
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        team.setFoundedOn(request.foundedOn());
        if (request.captainPlayerId() != null) {
            PlayerProfile captain = requireCaptainCandidate(request.captainPlayerId());
            team.setCaptainId(captain.getId());
            teamRepository.save(team);
            ensureActiveMembership(team.getId(), captain.getId());
            roleService.grantApproved(
                    userRepository.findById(captain.getUserId()).orElseThrow(),
                    Role.CAPTAIN,
                    null
            );
        } else {
            teamRepository.save(team);
        }
        return toTeamResponse(team);
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        return toTeamResponse(requireTeam(teamId));
    }

    @Transactional(readOnly = true)
    public TeamResponse myTeam(UserPrincipal principal) {
        if (!principal.hasAnyRole(Role.PLAYER, Role.CAPTAIN, Role.ADMIN, Role.REFEREE)) {
            throw ApiException.forbidden("У болельщика нет вкладки «Моя команда»");
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Сначала заполните профиль игрока"));
        return teamMemberRepository.findByPlayerIdAndStatus(profile.getId(), TeamMemberStatus.ACTIVE).stream()
                .map(member -> teamRepository.findById(member.getTeamId()).orElse(null))
                .filter(team -> team != null && !team.isDisbanded())
                .findFirst()
                .map(this::toTeamResponse)
                .orElseThrow(() -> ApiException.notFound("Вы ещё не в команде"));
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> listTeams(String query, boolean includeDisbanded, Pageable pageable) {
        boolean hasQuery = query != null && !query.isBlank();
        Page<Team> page;
        if (includeDisbanded) {
            page = hasQuery
                    ? teamRepository.findByNameContainingIgnoreCase(query.trim(), pageable)
                    : teamRepository.findAll(pageable);
        } else {
            page = hasQuery
                    ? teamRepository.findByDisbandedFalseAndNameContainingIgnoreCase(query.trim(), pageable)
                    : teamRepository.findByDisbandedFalse(pageable);
        }
        return page.map(this::toTeamResponse);
    }

    @Transactional
    public TeamResponse updateTeam(UserPrincipal principal, UUID teamId, UpdateTeamRequest request) {
        Team team = requireActiveTeam(teamId);
        assertCanManageTeam(principal, team);

        if (request.name() != null && !request.name().isBlank()) {
            team.setName(request.name().trim());
        }
        if (request.shortName() != null) {
            team.setShortName(request.shortName());
        }
        if (request.logoUrl() != null) {
            team.setLogoUrl(request.logoUrl());
        }
        if (request.foundedOn() != null) {
            if (!principal.hasRole(Role.ADMIN)) {
                throw ApiException.forbidden("Дату основания меняет только админ");
            }
            team.setFoundedOn(request.foundedOn());
        }
        if (request.captainPlayerId() != null) {
            if (!principal.hasRole(Role.ADMIN)) {
                throw ApiException.forbidden("Капитана назначает только админ");
            }
            bindCaptain(team, request.captainPlayerId());
        }
        return toTeamResponse(teamRepository.save(team));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(UUID teamId) {
        requireTeam(teamId);
        return teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(UserPrincipal principal, UUID teamId, AddTeamMemberRequest request) {
        Team team = requireActiveTeam(teamId);
        assertCanManageTeam(principal, team);

        PlayerProfile player = playerProfileRepository.findById(request.playerId())
                .orElseThrow(() -> ApiException.notFound("Player not found"));
        if (!roleService.hasApproved(player.getUserId(), Role.PLAYER)
                && !roleService.hasApproved(player.getUserId(), Role.CAPTAIN)) {
            throw ApiException.badRequest("Игрока можно добавить только после подтверждения роли ИГРОК");
        }

        TeamMember existing = teamMemberRepository.findByTeamIdAndPlayerId(teamId, player.getId()).orElse(null);
        if (existing != null && existing.getStatus() == TeamMemberStatus.ACTIVE) {
            throw ApiException.conflict("Player is already an active member of this team");
        }

        TeamMember member = existing == null ? new TeamMember() : existing;
        member.setTeamId(teamId);
        member.setPlayerId(player.getId());
        member.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.save(member);

        notificationService.publishToUser(
                player.getUserId(),
                NotificationEventType.TEAM_INVITATION,
                "Added to team",
                "You were added to " + team.getName(),
                Map.of("teamId", teamId.toString(), "playerId", player.getId().toString())
        );

        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(UserPrincipal principal, UUID teamId, UUID playerId) {
        Team team = requireActiveTeam(teamId);
        assertCanManageTeam(principal, team);

        if (playerId.equals(team.getCaptainId())) {
            throw ApiException.badRequest("Cannot remove the team captain; assign a new captain first");
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndPlayerId(teamId, playerId)
                .orElseThrow(() -> ApiException.notFound("Team member not found"));
        member.setStatus(TeamMemberStatus.REMOVED);
        teamMemberRepository.save(member);
    }

    @Transactional
    public TeamResponse assignCaptain(UserPrincipal principal, UUID teamId, AssignCaptainRequest request) {
        if (!principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Капитана назначает только админ");
        }
        Team team = requireActiveTeam(teamId);
        bindCaptain(team, request.playerId());
        return toTeamResponse(teamRepository.save(team));
    }

    @Transactional
    public void disbandTeam(UserPrincipal principal, UUID teamId) {
        if (!principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Расформировать команду может только админ");
        }
        Team team = requireActiveTeam(teamId);
        team.setDisbanded(true);
        teamRepository.save(team);
        for (TeamMember member : teamMemberRepository.findByTeamId(teamId)) {
            member.setStatus(TeamMemberStatus.REMOVED);
            teamMemberRepository.save(member);
        }
        for (TournamentTeam entry : tournamentTeamRepository.findByTeamId(teamId)) {
            if (entry.getStatus() != TournamentTeamStatus.WITHDRAWN) {
                entry.setStatus(TournamentTeamStatus.WITHDRAWN);
                tournamentTeamRepository.save(entry);
            }
        }
    }

    private void bindCaptain(Team team, UUID playerId) {
        PlayerProfile captain = requireCaptainCandidate(playerId);
        if (!teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(team.getId(), captain.getId(), TeamMemberStatus.ACTIVE)) {
            ensureActiveMembership(team.getId(), captain.getId());
        }
        team.setCaptainId(captain.getId());
        User user = userRepository.findById(captain.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        roleService.grantApproved(user, Role.CAPTAIN, user.getPhotoUrl());
    }

    private PlayerProfile requireCaptainCandidate(UUID playerId) {
        return playerProfileRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Player not found"));
    }

    private void assertCanManageTeam(UserPrincipal principal, Team team) {
        if (principal.hasRole(Role.ADMIN)) {
            return;
        }
        if (!principal.hasRole(Role.CAPTAIN)) {
            throw ApiException.forbidden("Only the team captain can manage this team");
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Only the team captain can manage this team"));
        if (!profile.getId().equals(team.getCaptainId())) {
            throw ApiException.forbidden("Only the team captain can manage this team");
        }
    }

    private void ensureActiveMembership(UUID teamId, UUID playerId) {
        TeamMember membership = teamMemberRepository.findByTeamIdAndPlayerId(teamId, playerId).orElseGet(TeamMember::new);
        membership.setTeamId(teamId);
        membership.setPlayerId(playerId);
        membership.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.save(membership);
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    private Team requireActiveTeam(UUID teamId) {
        Team team = requireTeam(teamId);
        if (team.isDisbanded()) {
            throw ApiException.badRequest("Команда уже расформирована");
        }
        return team;
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                team.getCaptainId(),
                team.getFoundedOn(),
                team.isDisbanded(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    private TeamMemberResponse toMemberResponse(TeamMember member) {
        PlayerProfile player = playerProfileRepository.findById(member.getPlayerId()).orElse(null);
        return new TeamMemberResponse(
                member.getId(),
                member.getTeamId(),
                member.getPlayerId(),
                player == null ? null : player.getFirstName(),
                player == null ? null : player.getLastName(),
                player == null ? null : player.getDisplayName(),
                player == null ? null : player.getJerseyNumber(),
                player == null ? null : player.getPosition(),
                member.getJoinedAt(),
                member.getStatus()
        );
    }
}
