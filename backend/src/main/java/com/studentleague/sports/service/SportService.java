package com.studentleague.sports.service;

import com.studentleague.sports.dto.SportResponse;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class SportService {

    private final SportRepository sportRepository;

    public SportService(SportRepository sportRepository) {
        this.sportRepository = sportRepository;
    }

    @Transactional(readOnly = true)
    public List<SportResponse> listSports() {
        return sportRepository.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Sport sport) -> switch (sport.getCode()) {
                            case "FOOTBALL" -> 0;
                            case "FUTSAL" -> 1;
                            default -> 10;
                        })
                        .thenComparing(Sport::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new SportResponse(s.getId(), s.getName(), s.getCode()))
                .toList();
    }
}
