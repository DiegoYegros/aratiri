package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;

import java.util.function.Supplier;

public interface OnChainPaymentCommand {

    OnChainPaymentDTOs.SendOnChainResponseDTO execute(
            String userId,
            String idempotencyKey,
            OnChainPaymentDTOs.SendOnChainRequestDTO request,
            Supplier<OnChainPaymentDTOs.SendOnChainResponseDTO> execution
    );
}
