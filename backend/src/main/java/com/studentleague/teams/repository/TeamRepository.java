package com.studentleague.teams.repository;

import com.studentleague.teams.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Page<Team> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
