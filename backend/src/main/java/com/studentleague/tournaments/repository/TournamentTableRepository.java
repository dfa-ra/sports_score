package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.TournamentTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TournamentTableRepository extends JpaRepository<TournamentTable, UUID> {
    List<TournamentTable> findByTournamentIdOrderBySortOrderAscIdAsc(UUID tournamentId);
}
