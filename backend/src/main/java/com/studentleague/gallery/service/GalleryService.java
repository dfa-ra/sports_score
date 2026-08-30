package com.studentleague.gallery.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.gallery.domain.GallerySlot;
import com.studentleague.gallery.dto.CreateGalleryPhotoRequest;
import com.studentleague.gallery.dto.GalleryFeedResponse;
import com.studentleague.gallery.dto.GalleryPhotoResponse;
import com.studentleague.gallery.entity.GalleryPhoto;
import com.studentleague.gallery.entity.SiteSetting;
import com.studentleague.gallery.repository.GalleryPhotoRepository;
import com.studentleague.gallery.repository.SiteSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GalleryService {

    public static final String VK_ALBUM_KEY = "vk_album_url";

    private final GalleryPhotoRepository photoRepository;
    private final SiteSettingRepository settingRepository;

    public GalleryService(GalleryPhotoRepository photoRepository, SiteSettingRepository settingRepository) {
        this.photoRepository = photoRepository;
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public GalleryFeedResponse feed() {
        return new GalleryFeedResponse(
                settingRepository.findById(VK_ALBUM_KEY).map(SiteSetting::getValue).orElse(null),
                photoRepository.findAllByOrderBySortOrderAscCreatedAtDesc().stream()
                        .map(GalleryService::toResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<GalleryPhotoResponse> enabledSlot(GallerySlot slot) {
        return photoRepository.findBySlotAndEnabledTrueOrderBySortOrderAscCreatedAtDesc(slot).stream()
                .map(GalleryService::toResponse)
                .toList();
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
        return feed();
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
