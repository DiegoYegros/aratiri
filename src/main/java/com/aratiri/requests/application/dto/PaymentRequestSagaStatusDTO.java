package com.aratiri.requests.application.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentRequestSagaStatusDTO {
    long provisioningDue;
    long provisioningInProgress;
    long provisioningFailed;
    long cancellationDue;
    long cancellationInProgress;
    long cancellationExhausted;
}
