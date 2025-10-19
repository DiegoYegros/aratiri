package com.aratiri.dto.invoices;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInvoiceDTO {
    @JsonProperty(value = "payment_request")
    private String paymentRequest;

}
