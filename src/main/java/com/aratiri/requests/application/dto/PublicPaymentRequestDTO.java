package com.aratiri.requests.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicPaymentRequestDTO {

    @JsonProperty("public_id")
    private String publicId;

    @JsonProperty("amount_sats")
    private long amountSats;

    @JsonProperty("memo")
    private String memo;

    @JsonProperty("status")
    private String status;

    @JsonProperty("payment_request")
    private String paymentRequest;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("expires_at")
    private String expiresAt;

    @JsonProperty("paid_at")
    private String paidAt;

    @JsonProperty("cancelled_at")
    private String cancelledAt;
}
