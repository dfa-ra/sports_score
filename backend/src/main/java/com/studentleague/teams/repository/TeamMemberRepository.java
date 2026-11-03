package com.studentleague.teams.repository;

import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByTeamIdAndStatus(UUID teamId, TeamMemberStatus status);
    List<TeamMember> findByPlayerIdAndStatus(UUID playerId, TeamMemberStatus status);
    List<TeamMember> findByPlayerIdInAndStatus(Collection<UUID> playerIds, TeamMemberStatus status);
    Optional<TeamMember> findByTeamIdAndPlayerId(UUID teamId, UUID playerId);
    boolean existsByTeamIdAndPlayerIdAndStatus(UUID teamId, UUID playerId, TeamMemberStatus status);
    List<TeamMember> findByTeamId(UUID teamId);
    void deleteByTeamId(UUID teamId);

    @Query("""
            select count(distinct member.playerId) from TeamMember member
            where member.status = com.studentleague.teams.domain.TeamMemberStatus.ACTIVE
            """)
    long countDistinctActivePlayers();

    @Query("""
            select count(distinct member.playerId) from TeamMember member
            where member.status = com.studentleague.teams.domain.TeamMemberStatus.ACTIVE
              and member.teamId in (
                select entry.teamId from TournamentTeam entry
                where entry.tournamentId = :tournamentId
                  and entry.status = com.studentleague.tournaments.domain.TournamentTeamStatus.APPROVED
              )
            """)
    long countDistinctActivePlayersInTournament(@Param("tournamentId") UUID tournamentId);
}
