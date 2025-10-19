package com.aratiri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "NODE_SETTINGS")
@Data
public class NodeSettingsEntity {

    @Id
    private String id = "singleton";

    @Column(nullable = false)
    private boolean autoManagePeers = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public NodeSettingsEntity() {
    }

    public NodeSettingsEntity(boolean autoManagePeers) {
        this.id = "singleton";
        this.autoManagePeers = autoManagePeers;
    }
}