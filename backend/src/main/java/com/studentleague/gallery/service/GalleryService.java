package com.studentleague.gallery.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import com.studentleague.gallery.GalleryMediaPaths;
import com.studentleague.gallery.domain.GallerySlot;
import com.studentleague.gallery.dto.CreateGalleryPhotoRequest;
import com.studentleague.gallery.dto.GalleryFeedResponse;
import com.studentleague.gallery.dto.GalleryPhotoResponse;
import com.studentleague.gallery.entity.GalleryPhoto;
import com.studentleague.gallery.entity.SiteSetting;
import com.studentleague.gallery.repository.GalleryPhotoRepository;
import com.studentleague.gallery.repository.SiteSettingRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class GalleryService {

    public static final String VK_ALBUM_KEY = "vk_album_url";

    private final GalleryPhotoRepository photoRepository;
    private final SiteSettingRepository settingRepository;
    private final Path uploadRoot;

    public GalleryService(
            GalleryPhotoRepository photoRepository,
            SiteSettingRepository settingRepository,
            AppProperties appProperties
    ) {
        this.photoRepository = photoRepository;
        this.settingRepository = settingRepository;
        AppProperties.LocalStorage local = appProperties.localStorage();
        String root = local == null || local.rootDir() == null || local.rootDir().isBlank()
                ? "./data/uploads"
                : local.rootDir();
        this.uploadRoot = Path.of(root).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public GalleryFeedResponse feed() {
        return new GalleryFeedResponse(vkAlbumUrl(), photos(photoRepository.findEnabled()));
    }

    @Transactional(readOnly = true)
    public GalleryFeedResponse adminFeed() {
        return new GalleryFeedResponse(vkAlbumUrl(), photos(photoRepository.findAllByOrderBySortOrderAscCreatedAtDesc()));
    }

    @Transactional(readOnly = true)
    public List<GalleryPhotoResponse> enabledSlot(GallerySlot slot) {
        return photos(photoRepository.findEnabledBySlot(slot));
    }

    /**
     * True when this /media path belongs only to disabled gallery photos.
     * An enabled slide that uses the same file stays public.
     */
    @Transactional(readOnly = true)
    public boolean isHiddenFromAnonymous(String requestUri) {
        String path = GalleryMediaPaths.requestPath(requestUri);
        if (!path.startsWith("/media/gallery/")) {
            return false;
        }
        boolean seen = false;
        for (GalleryPhoto photo : photoRepository.findAll()) {
            String photoPath = GalleryMediaPaths.photoPath(photo.getUrl());
            if (photoPath == null || !photoPath.equals(path)) {
                continue;
            }
            if (photo.isEnabled()) {
                return false;
            }
            seen = true;
        }
        return seen;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> openFile(UUID id) {
        GalleryPhoto photo = photoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Фото не найдено"));
        Path file = storedFile(photo.getUrl());
        if (file == null || !Files.isRegularFile(file)) {
            throw ApiException.notFound("Файл не найден");
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String probed = Files.probeContentType(file);
            if (probed != null && !probed.isBlank()) {
                type = MediaType.parseMediaType(probed);
            }
        } catch (IOException ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(file));
    }

    @Transactional
    public GalleryPhotoResponse add(CreateGalleryPhotoRequest request) {
        GalleryPhoto photo = new GalleryPhoto();
        apply(photo, request);
        if (photo.getSource() == null) {
            photo.setSource("URL");
        }
        return toResponse(photoRepository.save(photo));
    }

    @Transactional
    public GalleryPhotoResponse update(UUID id, CreateGalleryPhotoRequest request) {
        GalleryPhoto photo = photoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Фото не найдено"));
        apply(photo, request);
        return toResponse(photoRepository.save(photo));
    }

    @Transactional
    public void delete(UUID id) {
        if (!photoRepository.existsById(id)) {
            throw ApiException.notFound("Фото не найдено");
        }
        photoRepository.deleteById(id);
    }

    @Transactional
    public GalleryFeedResponse setVkAlbum(String url) {
        SiteSetting setting = settingRepository.findById(VK_ALBUM_KEY).orElseGet(SiteSetting::new);
        setting.setKey(VK_ALBUM_KEY);
        setting.setValue(url == null || url.isBlank() ? null : url.trim());
        settingRepository.save(setting);
        return adminFeed();
    }

    private String vkAlbumUrl() {
        return settingRepository.findById(VK_ALBUM_KEY).map(SiteSetting::getValue).orElse(null);
    }

    private static List<GalleryPhotoResponse> photos(List<GalleryPhoto> rows) {
        return rows.stream().map(GalleryService::toResponse).toList();
    }

    private Path storedFile(String url) {
        String mediaPath = GalleryMediaPaths.photoPath(url);
        if (mediaPath == null || !mediaPath.startsWith("/media/")) {
            return null;
        }
        String relative = mediaPath.substring("/media/".length());
        if (relative.isBlank() || relative.contains("..")) {
            return null;
        }
        Path file = uploadRoot.resolve(relative).normalize();
        if (!file.startsWith(uploadRoot)) {
            return null;
        }
        return file;
    }

    private void apply(GalleryPhoto photo, CreateGalleryPhotoRequest request) {
        photo.setUrl(request.url().trim());
        photo.setTitle(blankToNull(request.title()));
        photo.setCaption(blankToNull(request.caption()));
        photo.setLinkUrl(blankToNull(request.linkUrl()));
        photo.setLinkLabel(blankToNull(request.linkLabel()));
        photo.setSlot(request.slot() == null ? GallerySlot.GALLERY : request.slot());
        photo.setSortOrder(request.sortOrder() == null ? photo.getSortOrder() : request.sortOrder());
        photo.setEnabled(request.enabled() == null || request.enabled());
        if (request.source() != null && !request.source().isBlank()) {
            photo.setSource(request.source().trim());
        } else if (photo.getSource() == null) {
            photo.setSource("URL");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static GalleryPhotoResponse toResponse(GalleryPhoto photo) {
        return new GalleryPhotoResponse(
                photo.getId(),
                photo.getUrl(),
                photo.getTitle(),
                photo.getCaption(),
                photo.getLinkUrl(),
                photo.getLinkLabel(),
                photo.getSlot(),
                photo.isEnabled(),
                photo.getSource(),
                photo.getSortOrder(),
                photo.getCreatedAt()
        );
    }
}
