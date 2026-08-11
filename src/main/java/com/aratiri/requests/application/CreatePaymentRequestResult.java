package com.aratiri.requests.application;

import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;

/**
 * Create outcome used by the HTTP adapter to choose 202 (new create) / 202 or 200 (same-key replay).
 */
public record CreatePaymentRequestResult(
        OwnerPaymentRequestDTO body,
        boolean newlyCreated
) {
}
