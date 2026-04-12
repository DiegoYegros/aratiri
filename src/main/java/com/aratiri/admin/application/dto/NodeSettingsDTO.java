package com.aratiri.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class NodeSettingsDTO {
    @JsonProperty("auto_manage_peers")
    private boolean autoManagePeers;

    @JsonProperty("transaction_reconciliation_min_age_ms")
    private long transactionReconciliationMinAgeMs;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
