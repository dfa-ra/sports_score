package com.studentleague.home.controller;

import com.studentleague.gallery.dto.GalleryFeedResponse;
import com.studentleague.gallery.service.GalleryService;
import com.studentleague.home.dto.HomeFeedResponse;
import com.studentleague.statistics.service.StatisticsService;
import com.studentleague.tournaments.dto.TournamentResponse;
import com.studentleague.tournaments.service.TournamentService;
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

    public HomeController(
            TournamentService tournamentService,
            StatisticsService statisticsService,
            GalleryService galleryService
    ) {
        this.tournamentService = tournamentService;
        this.statisticsService = statisticsService;
        this.galleryService = galleryService;
    }

    @GetMapping
    @Operation(summary = "Home widgets: table, top-5, photos")
    public HomeFeedResponse home() {
        TournamentResponse tournament = tournamentService.current();
        GalleryFeedResponse gallery = galleryService.feed();
        return new HomeFeedResponse(
                tournament,
                tournament == null ? List.of() : tournamentService.standings(tournament.id()),
                tournament == null ? statisticsService.scorers(null, 5) : statisticsService.scorers(tournament.id(), 5),
                tournament == null ? statisticsService.assists(null, 5) : statisticsService.assists(tournament.id(), 5),
                gallery.photos(),
                gallery.vkAlbumUrl()
        );
    }
}
