package com.aratiri.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class NodeSettingsDTO {
    @JsonProperty("auto_manage_peers")
    private boolean autoManagePeers;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}