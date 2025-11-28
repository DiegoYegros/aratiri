package com.aratiri.lnurl.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LnurlPayRequestDTO {

    @NotBlank(message = "Callback URL cannot be blank")
    private String callback;

    @NotNull(message = "Amount in millisatoshis cannot be null")
    @JsonProperty("amount_msat")
    private Long amountMsat;

    private String comment;
}