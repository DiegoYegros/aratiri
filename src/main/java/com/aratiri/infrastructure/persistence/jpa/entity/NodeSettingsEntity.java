package com.aratiri.infrastructure.persistence.jpa.entity;

import com.aratiri.admin.domain.NodeSettings;
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
    private String id = NodeSettings.SINGLETON_ID;

    @Column(nullable = false)
    private boolean autoManagePeers = false;

    @Column(nullable = false)
    private long transactionReconciliationMinAgeMs = NodeSettings.DEFAULT_TRANSACTION_RECONCILIATION_MIN_AGE_MS;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public NodeSettingsEntity() {
    }

    public NodeSettingsEntity(boolean autoManagePeers, long transactionReconciliationMinAgeMs) {
        this.id = NodeSettings.SINGLETON_ID;
        this.autoManagePeers = autoManagePeers;
        this.transactionReconciliationMinAgeMs = transactionReconciliationMinAgeMs;
    }
}
