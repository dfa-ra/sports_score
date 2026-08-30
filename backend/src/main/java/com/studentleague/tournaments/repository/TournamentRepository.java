package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.entity.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    Page<Tournament> findByStatus(TournamentStatus status, Pageable pageable);
    Page<Tournament> findBySportId(UUID sportId, Pageable pageable);
    Page<Tournament> findByStatusAndSportId(TournamentStatus status, UUID sportId, Pageable pageable);
    List<Tournament> findByStatusOrderByStartDateDescCreatedAtDesc(TournamentStatus status);
    List<Tournament> findAllByOrderByStartDateDescCreatedAtDesc();
}
