package com.aratiri.invoices.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateInvoiceRequestDTO {
    @JsonProperty("sats_amount")
    private long satsAmount;
    @JsonProperty("memo")
    @NotNull(message = "memo no puede ser nulo.")
    private String memo;
    @JsonProperty("external_reference")
    private String externalReference;
    @JsonProperty("metadata")
    private String metadata;
}
