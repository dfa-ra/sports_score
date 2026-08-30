package com.studentleague.gallery.entity;

import com.studentleague.gallery.domain.GallerySlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gallery_photos")
public class GalleryPhoto {

    @Id
    private UUID id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 300)
    private String caption;

    @Column(length = 200)
    private String title;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "link_label", length = 80)
    private String linkLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GallerySlot slot = GallerySlot.GALLERY;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getLinkLabel() {
        return linkLabel;
    }

    public void setLinkLabel(String linkLabel) {
        this.linkLabel = linkLabel;
    }

    public GallerySlot getSlot() {
        return slot;
    }

    public void setSlot(GallerySlot slot) {
        this.slot = slot == null ? GallerySlot.GALLERY : slot;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
