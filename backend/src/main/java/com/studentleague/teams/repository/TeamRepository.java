package com.studentleague.teams.repository;

import com.studentleague.teams.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findFirstByDisbandedFalseAndNameIgnoreCase(String name);

    List<Team> findByDisbandedFalseAndNameContainingIgnoreCase(String name);

    Page<Team> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Team> findByDisbandedFalse(Pageable pageable);
    Page<Team> findByDisbandedFalseAndNameContainingIgnoreCase(String name, Pageable pageable);
}
