package com.aratiri.aratiri.dto.payments;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayInvoiceRequestDTO {

    @NotBlank(message = "Invoice cannot be blank")
    private String invoice;
    private Long feeLimitSat;
    private Integer timeoutSeconds;
}