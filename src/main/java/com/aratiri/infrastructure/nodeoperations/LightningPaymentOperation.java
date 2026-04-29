package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LightningPaymentOperation(
        String transactionId,
        String userId,
        String paymentHash,
        String invoice,
        Long feeLimitSat,
        Integer timeoutSeconds,
        @JsonProperty("external_reference") String externalReference,
        String metadata
) {

    public static LightningPaymentOperation fromPaymentRequest(
            String transactionId,
            String userId,
            PayInvoiceRequestDTO request
    ) {
        return new LightningPaymentOperation(
                transactionId,
                userId,
                null,
                request.getInvoice(),
                request.getFeeLimitSat(),
                request.getTimeoutSeconds(),
                request.getExternalReference(),
                request.getMetadata()
        );
    }

    LightningPaymentOperation withPaymentHash(String paymentHash) {
        return new LightningPaymentOperation(
                transactionId,
                userId,
                paymentHash,
                invoice,
                feeLimitSat,
                timeoutSeconds,
                externalReference,
                metadata
        );
    }

    PayInvoiceRequestDTO toPaymentRequest() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice(invoice);
        request.setFeeLimitSat(feeLimitSat);
        request.setTimeoutSeconds(timeoutSeconds);
        request.setExternalReference(externalReference);
        request.setMetadata(metadata);
        return request;
    }
}
