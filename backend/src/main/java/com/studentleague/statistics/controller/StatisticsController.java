package com.studentleague.statistics.controller;

import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.dto.TeamStatisticsResponse;
import com.studentleague.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statistics")
@Tag(name = "Statistics")
@SecurityRequirement(name = "bearerAuth")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/players")
    @Operation(summary = "Player statistics aggregated from MatchEvent")
    public List<PlayerStatisticsResponse> players(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(required = false) Integer seasonYear,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID playerId
    ) {
        return statisticsService.playerStatistics(tournamentId, seasonYear, teamId, playerId);
    }

    @GetMapping("/teams")
    @Operation(summary = "Team statistics from finished matches")
    public List<TeamStatisticsResponse> teams(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(required = false) Integer seasonYear,
            @RequestParam(required = false) UUID teamId
    ) {
        return statisticsService.teamStatistics(tournamentId, seasonYear, teamId);
    }
}
