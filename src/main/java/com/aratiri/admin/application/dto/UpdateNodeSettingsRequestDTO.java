package com.aratiri.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateNodeSettingsRequestDTO {
    @JsonProperty("auto_manage_peers")
    private Boolean autoManagePeers;

    @PositiveOrZero
    @JsonProperty("transaction_reconciliation_min_age_ms")
    private Long transactionReconciliationMinAgeMs;
}
