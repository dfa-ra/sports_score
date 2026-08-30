package com.studentleague.gallery.controller;

import com.studentleague.gallery.dto.CreateGalleryPhotoRequest;
import com.studentleague.gallery.dto.GalleryFeedResponse;
import com.studentleague.gallery.dto.GalleryPhotoResponse;
import com.studentleague.gallery.service.GalleryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Gallery")
public class GalleryController {

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @GetMapping("/gallery")
    @Operation(summary = "Public match photo gallery")
    public GalleryFeedResponse feed() {
        return galleryService.feed();
    }

    @PostMapping("/admin/gallery")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a photo URL (VK album link or uploaded file URL)")
    public GalleryPhotoResponse add(@Valid @RequestBody CreateGalleryPhotoRequest request) {
        return galleryService.add(request);
    }

    @PutMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a home/gallery slide")
    public GalleryPhotoResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGalleryPhotoRequest request
    ) {
        return galleryService.update(id, request);
    }

    @DeleteMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        galleryService.delete(id);
    }

    @PutMapping("/admin/gallery/vk-album")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Store VK album URL for the home gallery")
    public GalleryFeedResponse vkAlbum(@RequestBody Map<String, String> body) {
        return galleryService.setVkAlbum(body.get("url"));
    }
}
