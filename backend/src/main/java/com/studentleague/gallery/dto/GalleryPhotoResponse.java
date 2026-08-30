package com.studentleague.gallery.dto;

import java.time.Instant;
import java.util.UUID;

public record GalleryPhotoResponse(
        UUID id,
        String url,
        String caption,
        String source,
        int sortOrder,
        Instant createdAt
) {
}
