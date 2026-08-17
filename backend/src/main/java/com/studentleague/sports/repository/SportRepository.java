package com.studentleague.sports.repository;

import com.studentleague.sports.entity.Sport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SportRepository extends JpaRepository<Sport, UUID> {
    Optional<Sport> findByCodeIgnoreCase(String code);
}
