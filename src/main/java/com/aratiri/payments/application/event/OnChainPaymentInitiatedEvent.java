package com.aratiri.payments.application.event;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OnChainPaymentInitiatedEvent(
  String userId,
  String transactionId,
  OnChainPaymentRequest paymentRequest
) {

  public OnChainPaymentInitiatedEvent(
    String userId,
    String transactionId,
    OnChainPaymentDTOs.SendOnChainRequestDTO paymentRequest
  ) {
    this(userId, transactionId, OnChainPaymentRequest.from(paymentRequest));
  }

  public OnChainPaymentDTOs.SendOnChainRequestDTO getPaymentRequest() {
    return paymentRequest.toDto();
  }

  public String getUserId() {
    return userId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public record OnChainPaymentRequest(
    String address,
    @JsonProperty("sats_amount")
    Long satsAmount,
    @JsonProperty("sat_per_vbyte")
    Long satPerVbyte,
    @JsonProperty("target_conf")
    Integer targetConf,
    @JsonProperty("external_reference")
    String externalReference,
    String metadata
  ) {

    private static OnChainPaymentRequest from(OnChainPaymentDTOs.SendOnChainRequestDTO request) {
      return new OnChainPaymentRequest(
        request.getAddress(),
        request.getSatsAmount(),
        request.getSatPerVbyte(),
        request.getTargetConf(),
        request.getExternalReference(),
        request.getMetadata()
      );
    }

    private OnChainPaymentDTOs.SendOnChainRequestDTO toDto() {
      OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
      request.setAddress(address);
      request.setSatsAmount(satsAmount);
      request.setSatPerVbyte(satPerVbyte);
      request.setTargetConf(targetConf);
      request.setExternalReference(externalReference);
      request.setMetadata(metadata);
      return request;
    }
  }
}
