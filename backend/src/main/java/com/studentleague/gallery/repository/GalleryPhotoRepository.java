package com.studentleague.gallery.repository;

import com.studentleague.gallery.domain.GallerySlot;
import com.studentleague.gallery.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, UUID> {
    List<GalleryPhoto> findAllByOrderBySortOrderAscCreatedAtDesc();

    @Query("""
            select p from GalleryPhoto p
            where p.enabled = true
            order by p.sortOrder asc, p.createdAt desc
            """)
    List<GalleryPhoto> findEnabled();

    @Query("""
            select p from GalleryPhoto p
            where p.slot = :slot and p.enabled = true
            order by p.sortOrder asc, p.createdAt desc
            """)
    List<GalleryPhoto> findEnabledBySlot(@Param("slot") GallerySlot slot);
}
