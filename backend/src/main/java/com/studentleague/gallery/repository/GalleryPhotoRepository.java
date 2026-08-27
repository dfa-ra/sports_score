package com.studentleague.gallery.repository;

import com.studentleague.gallery.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, UUID> {
    List<GalleryPhoto> findAllByOrderBySortOrderAscCreatedAtDesc();
}
