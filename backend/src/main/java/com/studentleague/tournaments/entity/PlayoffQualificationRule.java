package com.studentleague.tournaments.entity;

import com.studentleague.tournaments.domain.QualifyStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "playoff_qualification_rules")
public class PlayoffQualificationRule {

    @Id
    private UUID id;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "place_from", nullable = false)
    private int placeFrom;

    @Column(name = "place_to", nullable = false)
    private int placeTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QualifyStage stage;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public void setTableId(UUID tableId) {
        this.tableId = tableId;
    }

    public int getPlaceFrom() {
        return placeFrom;
    }

    public void setPlaceFrom(int placeFrom) {
        this.placeFrom = placeFrom;
    }

    public int getPlaceTo() {
        return placeTo;
    }

    public void setPlaceTo(int placeTo) {
        this.placeTo = placeTo;
    }

    public QualifyStage getStage() {
        return stage;
    }

    public void setStage(QualifyStage stage) {
        this.stage = stage;
    }
}
