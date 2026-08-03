package com.aratiri.spark.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Registration payload. Carries ONLY public metadata — never the mnemonic.
 * `identity_public_key` is the compressed secp256k1 identity key from the
 * browser signer (66 hex chars, prefix 02/03).
 */
@Data
public class RegisterSparkWalletRequestDTO {

    @JsonProperty("identity_public_key")
    @NotBlank(message = "identity_public_key is required")
    @Pattern(
            regexp = "^(02|03)[0-9a-fA-F]{64}$",
            message = "identity_public_key must be a 66-char compressed secp256k1 public key (02 or 03 prefix)"
    )
    private String identityPublicKey;

    @JsonProperty("spark_address")
    @NotBlank(message = "spark_address is required")
    @Size(max = 128, message = "spark_address must be at most 128 characters")
    private String sparkAddress;

    @JsonProperty("network")
    @NotBlank(message = "network is required")
    private String network;

    @JsonProperty("account_index")
    @NotNull(message = "account_index is required")
    @Min(value = 0, message = "account_index must be non-negative")
    private Integer accountIndex;
}
