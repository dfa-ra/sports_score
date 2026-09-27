package com.studentleague.home.dto;

import com.studentleague.gallery.dto.GalleryPhotoResponse;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.dto.TournamentResponse;

import java.util.List;

public record HomeFeedResponse(
        TournamentResponse tournament,
        List<StandingTableResponse> standings,
        List<PlayerStatisticsResponse> scorers,
        List<PlayerStatisticsResponse> assists,
        List<GalleryPhotoResponse> heroes,
        List<GalleryPhotoResponse> stories,
        List<GalleryPhotoResponse> photos,
        String vkAlbumUrl
) {
}
