package com.studentleague.matches.repository;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    Page<Match> findByTournamentId(UUID tournamentId, Pageable pageable);
    Page<Match> findByStatus(MatchStatus status, Pageable pageable);
    Page<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status, Pageable pageable);
    List<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status);

    @Query("""
            select m from Match m
            where (m.homeTeamId = :teamId or m.awayTeamId = :teamId)
              and m.status = :status
            order by m.scheduledAt desc
            """)
    List<Match> findRecentByTeamAndStatus(
            @Param("teamId") UUID teamId,
            @Param("status") MatchStatus status,
            Pageable pageable
    );

    @Query("""
            select m from Match m
            where m.homeTeamId = :teamId or m.awayTeamId = :teamId
            """)
    Page<Match> findByTeamId(@Param("teamId") UUID teamId, Pageable pageable);
}
