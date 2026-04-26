package com.aratiri.webhooks.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookPayloadData {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("external_reference")
    private String externalReference;

    private String metadata;

    @JsonProperty("amount_sat")
    private Long amountSat;

    private String status;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("balance_after_sat")
    private Long balanceAfterSat;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("invoice_id")
    private String invoiceId;

    @JsonProperty("payment_hash")
    private String paymentHash;

    @JsonProperty("payment_request")
    private String paymentRequest;

    @JsonProperty("amount_paid_sat")
    private Long amountPaidSat;

    private String memo;

    @JsonProperty("operation_id")
    private String operationId;

    @JsonProperty("operation_type")
    private String operationType;
}
