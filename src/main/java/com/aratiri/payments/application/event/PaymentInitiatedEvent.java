package com.aratiri.payments.application.event;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentInitiatedEvent(
  String userId,
  String transactionId,
  PaymentRequest payRequest
) {

  public PaymentInitiatedEvent(String userId, String transactionId, PayInvoiceRequestDTO payRequest) {
    this(userId, transactionId, PaymentRequest.from(payRequest));
  }

  public PayInvoiceRequestDTO getPayRequest() {
    return payRequest.toDto();
  }

  public String getUserId() {
    return userId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public record PaymentRequest(
    String invoice,
    Long feeLimitSat,
    Integer timeoutSeconds,
    @JsonProperty("external_reference")
    String externalReference,
    String metadata
  ) {

    private static PaymentRequest from(PayInvoiceRequestDTO request) {
      return new PaymentRequest(
        request.getInvoice(),
        request.getFeeLimitSat(),
        request.getTimeoutSeconds(),
        request.getExternalReference(),
        request.getMetadata()
      );
    }

    private PayInvoiceRequestDTO toDto() {
      PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
      request.setInvoice(invoice);
      request.setFeeLimitSat(feeLimitSat);
      request.setTimeoutSeconds(timeoutSeconds);
      request.setExternalReference(externalReference);
      request.setMetadata(metadata);
      return request;
    }
  }
}
