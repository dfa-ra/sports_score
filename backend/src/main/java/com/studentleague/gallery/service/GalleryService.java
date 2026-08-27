package com.studentleague.gallery.service;

import com.studentleague.common.exception.ApiException;
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

    @Transactional
    public GalleryPhotoResponse add(CreateGalleryPhotoRequest request) {
        GalleryPhoto photo = new GalleryPhoto();
        photo.setUrl(request.url().trim());
        photo.setCaption(request.caption());
        photo.setSource(request.source() == null || request.source().isBlank() ? "URL" : request.source().trim());
        photo.setSortOrder(0);
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

    private static GalleryPhotoResponse toResponse(GalleryPhoto photo) {
        return new GalleryPhotoResponse(
                photo.getId(), photo.getUrl(), photo.getCaption(), photo.getSource(),
                photo.getSortOrder(), photo.getCreatedAt()
        );
    }
}
