package com.aratiri.requests.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePaymentRequestDTO {

    @JsonProperty("amount_sats")
    @Positive(message = "amount_sats must be positive")
    private long amountSats;

    @JsonProperty("memo")
    @Size(max = 500, message = "memo must be at most 500 characters")
    private String memo;

    /**
     * Seconds until the shareable request expires. The linked Lightning invoice
     * expiry is capped to this value so the invoice cannot outlive the request.
     */
    @JsonProperty("expires_in_seconds")
    @Min(value = 60, message = "expires_in_seconds must be at least 60")
    @Max(value = 604800, message = "expires_in_seconds must be at most 604800 (7 days)")
    private long expiresInSeconds;
}
