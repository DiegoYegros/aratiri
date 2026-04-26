package com.aratiri.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayInvoiceRequestDTO {

    @NotBlank(message = "Invoice cannot be blank")
    private String invoice;
    private Long feeLimitSat;
    private Integer timeoutSeconds;
    @JsonProperty("external_reference")
    private String externalReference;
    @JsonProperty("metadata")
    private String metadata;
}