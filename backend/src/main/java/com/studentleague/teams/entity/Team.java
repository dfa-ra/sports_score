package com.studentleague.teams.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 32)
    private String shortName;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "captain_id")
    private UUID captainId;

    @Column(nullable = false)
    private boolean disbanded = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public UUID getCaptainId() {
        return captainId;
    }

    public void setCaptainId(UUID captainId) {
        this.captainId = captainId;
    }

    public boolean isDisbanded() {
        return disbanded;
    }

    public void setDisbanded(boolean disbanded) {
        this.disbanded = disbanded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
