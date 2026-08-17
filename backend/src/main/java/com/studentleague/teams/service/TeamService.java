package com.studentleague.teams.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
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
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final UserRepository userRepository;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeamResponse createTeam(UserPrincipal principal, CreateTeamRequest request) {
        PlayerProfile creatorProfile = requireProfileForUser(principal.getId());

        Team team = new Team();
        team.setName(request.name().trim());
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        team.setCaptainId(creatorProfile.getId());
        teamRepository.save(team);

        TeamMember membership = new TeamMember();
        membership.setTeamId(team.getId());
        membership.setPlayerId(creatorProfile.getId());
        membership.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.save(membership);

        promoteToCaptain(creatorProfile.getUserId());

        return toTeamResponse(team);
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        return toTeamResponse(requireTeam(teamId));
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> listTeams(String query, Pageable pageable) {
        Page<Team> page = (query == null || query.isBlank())
                ? teamRepository.findAll(pageable)
                : teamRepository.findByNameContainingIgnoreCase(query.trim(), pageable);
        return page.map(this::toTeamResponse);
    }

    @Transactional
    public TeamResponse updateTeam(UserPrincipal principal, UUID teamId, UpdateTeamRequest request) {
        Team team = requireTeam(teamId);
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
        Team team = requireTeam(teamId);
        assertCanManageTeam(principal, team);

        PlayerProfile player = playerProfileRepository.findById(request.playerId())
                .orElseThrow(() -> ApiException.notFound("Player not found"));

        TeamMember existing = teamMemberRepository.findByTeamIdAndPlayerId(teamId, player.getId()).orElse(null);
        if (existing != null && existing.getStatus() == TeamMemberStatus.ACTIVE) {
            throw ApiException.conflict("Player is already an active member of this team");
        }

        TeamMember member = existing == null ? new TeamMember() : existing;
        member.setTeamId(teamId);
        member.setPlayerId(player.getId());
        member.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.save(member);

        User user = userRepository.findById(player.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == Role.FAN) {
            user.setRole(Role.PLAYER);
            userRepository.save(user);
        }

        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(UserPrincipal principal, UUID teamId, UUID playerId) {
        Team team = requireTeam(teamId);
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
        Team team = requireTeam(teamId);
        assertCanManageTeam(principal, team);

        if (!teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(teamId, request.playerId(), TeamMemberStatus.ACTIVE)) {
            throw ApiException.badRequest("Captain must be an active team member");
        }

        PlayerProfile newCaptain = playerProfileRepository.findById(request.playerId())
                .orElseThrow(() -> ApiException.notFound("Player not found"));

        team.setCaptainId(newCaptain.getId());
        teamRepository.save(team);
        promoteToCaptain(newCaptain.getUserId());
        return toTeamResponse(team);
    }

    private void assertCanManageTeam(UserPrincipal principal, Team team) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Only the team captain can manage this team"));
        if (!profile.getId().equals(team.getCaptainId())) {
            throw ApiException.forbidden("Only the team captain can manage this team");
        }
    }

    private void promoteToCaptain(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == Role.FAN || user.getRole() == Role.PLAYER) {
            user.setRole(Role.CAPTAIN);
            userRepository.save(user);
        }
    }

    private PlayerProfile requireProfileForUser(UUID userId) {
        return playerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("Create a player profile before creating a team"));
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                team.getCaptainId(),
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
