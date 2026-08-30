package com.studentleague.gallery.dto;

import java.util.List;

public record GalleryFeedResponse(
        String vkAlbumUrl,
        List<GalleryPhotoResponse> photos
) {
}
