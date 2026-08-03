package com.aratiri.requests.application;

import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;

/**
 * Create outcome used by the HTTP adapter to choose 201 / 202 / 200 without exposing saga internals.
 */
public record CreatePaymentRequestResult(
        OwnerPaymentRequestDTO body,
        boolean newlyCreated
) {
}
