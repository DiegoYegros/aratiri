package com.aratiri.spark.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Privacy-mode flag sync. {@code privacy_enabled} is the authoritative source
 * for the locked-dashboard render decision (hidden vs readable) — never inferred
 * from readonly-balance emptiness (UX design §5.5, principle #6).
 */
@Data
public class UpdateSparkPrivacyRequestDTO {

    @JsonProperty("privacy_enabled")
    @NotNull(message = "privacy_enabled is required")
    private Boolean privacyEnabled;
}
