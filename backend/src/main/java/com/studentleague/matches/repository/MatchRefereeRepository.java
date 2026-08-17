package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchReferee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRefereeRepository extends JpaRepository<MatchReferee, UUID> {
    List<MatchReferee> findByMatchId(UUID matchId);
    List<MatchReferee> findByRefereeId(UUID refereeId);
    Optional<MatchReferee> findByMatchIdAndRefereeId(UUID matchId, UUID refereeId);
    boolean existsByMatchIdAndRefereeId(UUID matchId, UUID refereeId);
}
