package com.studentleague.home.dto;

import com.studentleague.gallery.dto.GalleryPhotoResponse;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.TournamentResponse;

import java.util.List;

public record HomeFeedResponse(
        TournamentResponse tournament,
        List<StandingRow> standings,
        List<PlayerStatisticsResponse> scorers,
        List<PlayerStatisticsResponse> assists,
        List<GalleryPhotoResponse> photos,
        String vkAlbumUrl
) {
}
