package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;

public record OnChainSendOperationFact(
        String transactionId,
        String userId,
        String address,
        Long satsAmount,
        Long satPerVbyte,
        Integer targetConf,
        String externalReference,
        String metadata
) {

    OnChainPaymentDTOs.SendOnChainRequestDTO toRequestDto() {
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
