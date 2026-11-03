package com.studentleague.home.controller;

import com.studentleague.gallery.domain.GallerySlot;
import com.studentleague.gallery.service.GalleryService;
import com.studentleague.home.dto.HomeFeedResponse;
import com.studentleague.home.dto.HomeOverviewResponse;
import com.studentleague.home.service.HomeOverviewService;
import com.studentleague.statistics.service.StatisticsService;
import com.studentleague.tournaments.dto.TournamentResponse;
import com.studentleague.tournaments.service.TournamentService;

import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home")
public class HomeController {

    private final TournamentService tournamentService;
    private final StatisticsService statisticsService;
    private final GalleryService galleryService;
    private final HomeOverviewService homeOverviewService;

    public HomeController(
            TournamentService tournamentService,
            StatisticsService statisticsService,
            GalleryService galleryService,
            HomeOverviewService homeOverviewService
    ) {
        this.tournamentService = tournamentService;
        this.statisticsService = statisticsService;
        this.galleryService = galleryService;
        this.homeOverviewService = homeOverviewService;
    }

    @GetMapping("/overview")
    @Operation(summary = "League home: live, next, upcoming, recent matches, running tournaments and scorers")
    public HomeOverviewResponse overview() {
        return homeOverviewService.overview(Instant.now());
    }

    @GetMapping
    @Operation(summary = "Home widgets: table, top-5, photos")
    public HomeFeedResponse home() {
        TournamentResponse tournament = tournamentService.current();
        var board = statisticsService.board(tournament == null ? null : tournament.id(), 5);
        return new HomeFeedResponse(
                tournament,
                tournament == null ? List.of() : tournamentService.standings(tournament.id()).tables(),
                board.scorers(),
                board.assists(),
                galleryService.enabledSlot(GallerySlot.HERO),
                galleryService.enabledSlot(GallerySlot.STORY),
                galleryService.enabledSlot(GallerySlot.GALLERY),
                galleryService.feed().vkAlbumUrl()
        );
    }
}
