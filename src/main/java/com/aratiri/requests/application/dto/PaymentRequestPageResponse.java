package com.aratiri.requests.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaymentRequestPageResponse {

    @JsonProperty("payment_requests")
    private List<OwnerPaymentRequestDTO> paymentRequests;

    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("has_more")
    private boolean hasMore;
}
