package com.studentleague.gallery.dto;

import com.studentleague.gallery.domain.GallerySlot;

import java.time.Instant;
import java.util.UUID;

public record GalleryPhotoResponse(
        UUID id,
        String url,
        String title,
        String caption,
        String linkUrl,
        String linkLabel,
        GallerySlot slot,
        boolean enabled,
        String source,
        int sortOrder,
        Instant createdAt
) {
}
