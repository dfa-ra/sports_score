package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.PlayoffQualificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayoffQualificationRuleRepository extends JpaRepository<PlayoffQualificationRule, UUID> {

    List<PlayoffQualificationRule> findByTournamentIdOrderByPlaceFromAsc(UUID tournamentId);

    boolean existsByTournamentId(UUID tournamentId);

    void deleteByTournamentId(UUID tournamentId);
}
