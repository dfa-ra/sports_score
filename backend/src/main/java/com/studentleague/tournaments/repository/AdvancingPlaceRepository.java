package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.AdvancingPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdvancingPlaceRepository extends JpaRepository<AdvancingPlace, UUID> {
    List<AdvancingPlace> findByTournamentIdOrderByPlaceAsc(UUID tournamentId);

    void deleteByTournamentId(UUID tournamentId);
}
