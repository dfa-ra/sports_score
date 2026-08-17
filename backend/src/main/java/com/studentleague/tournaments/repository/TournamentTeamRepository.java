package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.TournamentTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentTeamRepository extends JpaRepository<TournamentTeam, UUID> {
    List<TournamentTeam> findByTournamentId(UUID tournamentId);
    Optional<TournamentTeam> findByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);
    boolean existsByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);
}
