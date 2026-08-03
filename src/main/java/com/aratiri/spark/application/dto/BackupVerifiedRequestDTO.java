package com.aratiri.spark.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Backup-verification flag sync. A UX flag only (the user completed the
 * onboarding verification step) — never a claim of custody.
 */
@Data
public class BackupVerifiedRequestDTO {

    @JsonProperty("backup_verified")
    @NotNull(message = "backup_verified is required")
    private Boolean backupVerified;
}
