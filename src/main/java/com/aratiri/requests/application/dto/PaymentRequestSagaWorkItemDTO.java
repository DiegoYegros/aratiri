package com.aratiri.requests.application.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentRequestSagaWorkItemDTO {
    String publicId;
    String status;
    String paymentHash;
    int provisionAttemptCount;
    String provisionLastError;
    int cancelAttemptCount;
    String cancelLastError;
    String createdAt;
    String expiresAt;
}
