package com.aratiri.admin.infrastructure.persistence;

import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.entity.NodeSettingsEntity;
import com.aratiri.repository.NodeSettingsRepository;
import org.springframework.stereotype.Component;

@Component
public class NodeSettingsAdapter implements NodeSettingsPort {

    private final NodeSettingsRepository nodeSettingsRepository;

    public NodeSettingsAdapter(NodeSettingsRepository nodeSettingsRepository) {
        this.nodeSettingsRepository = nodeSettingsRepository;
    }

    @Override
    public NodeSettings loadSettings() {
        NodeSettingsEntity settings = nodeSettingsRepository.findById("singleton")
                .orElseGet(() -> nodeSettingsRepository.save(new NodeSettingsEntity(false)));
        return toDomain(settings);
    }

    @Override
    public NodeSettings updateAutoManagePeers(boolean enabled) {
        NodeSettingsEntity entity = nodeSettingsRepository.findById("singleton")
                .orElseGet(() -> new NodeSettingsEntity(false));
        entity.setAutoManagePeers(enabled);
        NodeSettingsEntity updated = nodeSettingsRepository.save(entity);
        return toDomain(updated);
    }

    private NodeSettings toDomain(NodeSettingsEntity entity) {
        return new NodeSettings(entity.isAutoManagePeers(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
