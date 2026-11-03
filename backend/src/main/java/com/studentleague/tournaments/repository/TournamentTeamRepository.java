package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentTeamRepository extends JpaRepository<TournamentTeam, UUID> {
    List<TournamentTeam> findByTournamentId(UUID tournamentId);
    Optional<TournamentTeam> findByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);
    boolean existsByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);
    List<TournamentTeam> findByTeamId(UUID teamId);
    void deleteByTeamId(UUID teamId);

    long countByStatus(TournamentTeamStatus status);

    @Query("""
            select count(distinct entry.teamId) from TournamentTeam entry
            where entry.status = :status
            """)
    long countDistinctTeamsByStatus(@Param("status") TournamentTeamStatus status);

    @Query("""
            select count(distinct entry.teamId) from TournamentTeam entry
            where entry.tournamentId = :tournamentId and entry.status = :status
            """)
    long countDistinctTeamsByTournamentAndStatus(
            @Param("tournamentId") UUID tournamentId,
            @Param("status") TournamentTeamStatus status
    );
}
