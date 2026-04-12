package com.aratiri.admin.infrastructure.persistence;

import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeSettingsEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeSettingsRepository;
import org.springframework.stereotype.Component;

@Component
public class NodeSettingsAdapter implements NodeSettingsPort {

    private final NodeSettingsRepository nodeSettingsRepository;

    public NodeSettingsAdapter(NodeSettingsRepository nodeSettingsRepository) {
        this.nodeSettingsRepository = nodeSettingsRepository;
    }

    @Override
    public NodeSettings loadSettings() {
        NodeSettingsEntity settings = loadEntity();
        return toDomain(settings);
    }

    @Override
    public NodeSettings saveSettings(NodeSettings settings) {
        NodeSettingsEntity entity = loadEntity();
        entity.setAutoManagePeers(settings.autoManagePeers());
        entity.setTransactionReconciliationMinAgeMs(settings.transactionReconciliationMinAgeMs());
        NodeSettingsEntity updated = nodeSettingsRepository.save(entity);
        return toDomain(updated);
    }

    private NodeSettingsEntity loadEntity() {
        return nodeSettingsRepository.findById(NodeSettings.SINGLETON_ID)
                .orElseGet(() -> nodeSettingsRepository.save(
                        new NodeSettingsEntity(
                                false,
                                NodeSettings.DEFAULT_TRANSACTION_RECONCILIATION_MIN_AGE_MS
                        )
                ));
    }

    private NodeSettings toDomain(NodeSettingsEntity entity) {
        return new NodeSettings(
                entity.isAutoManagePeers(),
                entity.getTransactionReconciliationMinAgeMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
