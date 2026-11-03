package com.studentleague.matches.repository;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.RecentFormResponse;
import com.studentleague.matches.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    Page<Match> findByTournamentId(UUID tournamentId, Pageable pageable);
    List<Match> findByTournamentId(UUID tournamentId);
    long countByTournamentId(UUID tournamentId);

    @Query("select m from Match m where m.tournamentId in :tournamentIds")
    List<Match> findByTournamentIdIn(@Param("tournamentIds") java.util.Collection<UUID> tournamentIds);
    Page<Match> findByStatus(MatchStatus status, Pageable pageable);

    /**
     * Next kickoff: a live or paused match first, otherwise a scheduled match that has not started.
     * Equal kickoffs stay stable by id. A scheduled row in the past is not chosen.
     * {@code scheduledAt >= :from} is unknown for a null kickoff, so a dateless scheduled match
     * stays out. A live or paused match is still chosen when its kickoff is empty.
     */
    @Query("""
            select m from Match m
            where m.status in :inPlay
               or (m.status = :scheduled and m.scheduledAt >= :from)
            order by case when m.status in :inPlay then 0 else 1 end,
                     m.scheduledAt asc,
                     m.id asc
            """)
    List<Match> findNextByKickoff(
            @Param("inPlay") Collection<MatchStatus> inPlay,
            @Param("scheduled") MatchStatus scheduled,
            @Param("from") Instant from,
            Pageable pageable
    );

    /**
     * Same nearest-kickoff rule as {@link #findNextByKickoff}, limited to one tournament.
     * The collection is bound twice (filter and sort). Keep both queries on that shape.
     */
    @Query("""
            select m from Match m
            where m.tournamentId = :tournamentId
              and (m.status in :inPlay
               or (m.status = :scheduled and m.scheduledAt >= :from))
            order by case when m.status in :inPlay then 0 else 1 end,
                     m.scheduledAt asc,
                     m.id asc
            """)
    List<Match> findNextByKickoffForTournament(
            @Param("tournamentId") UUID tournamentId,
            @Param("inPlay") Collection<MatchStatus> inPlay,
            @Param("scheduled") MatchStatus scheduled,
            @Param("from") Instant from,
            Pageable pageable
    );
    List<Match> findByStatusIn(Collection<MatchStatus> statuses);
    Page<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status, Pageable pageable);
    List<Match> findByTournamentIdAndStatus(UUID tournamentId, MatchStatus status);

    @Query("""
            select m from Match m
            where m.tournamentId = :tournamentId
              and (m.homeTeamId = :teamId or m.awayTeamId = :teamId)
            """)
    List<Match> findByTournamentIdAndTeamId(
            @Param("tournamentId") UUID tournamentId,
            @Param("teamId") UUID teamId
    );

    boolean existsByHomeTeamIdOrAwayTeamId(UUID homeTeamId, UUID awayTeamId);

    List<Match> findByHomeGoalkeeperIdOrAwayGoalkeeperId(UUID homeGoalkeeperId, UUID awayGoalkeeperId);

    /**
     * Last finished matches for a team, with both clubs' names and crests.
     * One select: home and away teams are joined, not loaded per row.
     */
    @Query("""
            select new com.studentleague.matches.dto.RecentFormResponse(
                m.id,
                m.homeTeamId,
                home.name,
                home.shortName,
                home.logoUrl,
                m.awayTeamId,
                away.name,
                away.shortName,
                away.logoUrl,
                m.homeScore,
                m.awayScore,
                m.scheduledAt,
                m.status,
                m.startedAt
            )
            from Match m
            join Team home on home.id = m.homeTeamId
            join Team away on away.id = m.awayTeamId
            where (m.homeTeamId = :teamId or m.awayTeamId = :teamId)
              and m.status = :status
            order by coalesce(m.startedAt, m.scheduledAt) desc
            """)
    List<RecentFormResponse> findRecentForm(
            @Param("teamId") UUID teamId,
            @Param("status") MatchStatus status,
            Pageable pageable
    );

    @Query("""
            select m from Match m
            where (m.homeTeamId = :teamId or m.awayTeamId = :teamId)
              and m.status = :status
            """)
    List<Match> findByTeamIdAndStatus(
            @Param("teamId") UUID teamId,
            @Param("status") MatchStatus status
    );

    @Query("""
            select m from Match m
            where m.homeTeamId = :teamId or m.awayTeamId = :teamId
            """)
    Page<Match> findByTeamId(@Param("teamId") UUID teamId, Pageable pageable);
}
