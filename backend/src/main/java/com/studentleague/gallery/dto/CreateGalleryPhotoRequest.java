package com.studentleague.gallery.dto;

import com.studentleague.gallery.domain.GallerySlot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGalleryPhotoRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 200) String title,
        @Size(max = 300) String caption,
        @Size(max = 2048) String linkUrl,
        @Size(max = 80) String linkLabel,
        GallerySlot slot,
        Integer sortOrder,
        Boolean enabled,
        @Size(max = 32) String source
) {
}
