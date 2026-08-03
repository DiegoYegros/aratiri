package com.aratiri.spark.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Spark wallet metadata as served to the frontend. `privacyEnabled` is the
 * authoritative source for the locked-dashboard render decision (hidden vs
 * readable) — never inferred from readonly-balance emptiness.
 */
@Data
@Builder
public class SparkWalletDTO {

    @JsonProperty("spark_address")
    private String sparkAddress;

    @JsonProperty("identity_public_key")
    private String identityPublicKey;

    @JsonProperty("network")
    private String network;

    @JsonProperty("account_index")
    private int accountIndex;

    @JsonProperty("backup_verified")
    private boolean backupVerified;

    @JsonProperty("privacy_enabled")
    private boolean privacyEnabled;
}
