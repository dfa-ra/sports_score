package com.studentleague.teams.repository;

import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByTeamIdAndStatus(UUID teamId, TeamMemberStatus status);
    List<TeamMember> findByPlayerIdAndStatus(UUID playerId, TeamMemberStatus status);
    Optional<TeamMember> findByTeamIdAndPlayerId(UUID teamId, UUID playerId);
    boolean existsByTeamIdAndPlayerIdAndStatus(UUID teamId, UUID playerId, TeamMemberStatus status);
    List<TeamMember> findByTeamId(UUID teamId);
}
