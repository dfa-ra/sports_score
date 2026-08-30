package com.studentleague.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGalleryPhotoRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 300) String caption,
        @Size(max = 32) String source
) {
}
