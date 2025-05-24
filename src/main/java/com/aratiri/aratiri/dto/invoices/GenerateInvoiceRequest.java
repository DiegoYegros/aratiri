package com.aratiri.aratiri.dto.invoices;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateInvoiceRequest {
    @JsonProperty("sats_amount")
    private long satsAmount;
    @JsonProperty("memo")
    @NotNull(message = "memo no puede ser nulo.")
    private String memo;
}
