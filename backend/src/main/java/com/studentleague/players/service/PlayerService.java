package com.studentleague.players.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.players.dto.PlayerCardResponse;
import com.studentleague.players.dto.PlayerProfileRequest;
import com.studentleague.players.dto.PlayerProfileResponse;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
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
import java.util.Map;
import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerProfileRepository playerProfileRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public PlayerService(
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public PlayerProfileResponse createOrUpdateMyProfile(UUID userId, PlayerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        PlayerProfile profile = playerProfileRepository.findByUserId(userId).orElseGet(PlayerProfile::new);
        boolean creating = profile.getId() == null;
        profile.setUserId(userId);
        apply(profile, request);
        playerProfileRepository.save(profile);

        if (creating && (user.getRole() == Role.FAN)) {
            user.setRole(Role.PLAYER);
            userRepository.save(user);
        }

        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getById(UUID id) {
        return toResponse(requireProfile(id));
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getMyProfile(UUID userId) {
        PlayerProfile profile = playerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Player profile not found"));
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public Page<PlayerProfileResponse> list(String query, Pageable pageable) {
        Page<PlayerProfile> page;
        if (query == null || query.isBlank()) {
            page = playerProfileRepository.findAll(pageable);
        } else {
            String q = query.trim();
            page = playerProfileRepository
                    .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                            q, q, q, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PlayerCardResponse getPublicCard(UUID playerId) {
        PlayerProfile profile = requireProfile(playerId);
        List<TeamMember> memberships = teamMemberRepository.findByPlayerIdAndStatus(playerId, TeamMemberStatus.ACTIVE);
        PlayerCardResponse.TeamSummary teamSummary = null;
        if (!memberships.isEmpty()) {
            Team team = teamRepository.findById(memberships.getFirst().getTeamId()).orElse(null);
            if (team != null) {
                teamSummary = new PlayerCardResponse.TeamSummary(
                        team.getId(), team.getName(), team.getShortName(), team.getLogoUrl());
            }
        }
        return new PlayerCardResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getJerseyNumber(),
                profile.getPosition(),
                teamSummary,
                Map.of(),
                List.of()
        );
    }

    private PlayerProfile requireProfile(UUID id) {
        return playerProfileRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Player not found"));
    }

    private void apply(PlayerProfile profile, PlayerProfileRequest request) {
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.firstName() + " " + request.lastName()
                : request.displayName());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setJerseyNumber(request.jerseyNumber());
        profile.setPosition(request.position());
        profile.setBio(request.bio());
    }

    private PlayerProfileResponse toResponse(PlayerProfile profile) {
        return new PlayerProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getDateOfBirth(),
                profile.getAvatarUrl(),
                profile.getJerseyNumber(),
                profile.getPosition(),
                profile.getBio()
        );
    }
}
