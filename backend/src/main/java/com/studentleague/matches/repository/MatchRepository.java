package com.studentleague.matches.repository;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    Page<Match> findByTournamentId(UUID tournamentId, Pageable pageable);
    Page<Match> findByStatus(MatchStatus status, Pageable pageable);
    Page<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status, Pageable pageable);
    List<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status);
}
