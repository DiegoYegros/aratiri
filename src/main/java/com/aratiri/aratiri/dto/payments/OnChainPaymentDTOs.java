package com.aratiri.aratiri.dto.payments;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class OnChainPaymentDTOs {

    @Data
    public static class SendOnChainRequestDTO {
        @NotBlank(message = "Bitcoin address cannot be blank")
        private String address;

        @NotNull(message = "Amount in satoshis cannot be null")
        @JsonProperty("sats_amount")
        private Long satsAmount;

        @JsonProperty("sat_per_vbyte")
        private Long satPerVbyte;

        @JsonProperty("target_conf")
        private Integer targetConf;
    }

    @Data
    public static class SendOnChainResponseDTO {
        private String txid;
        private String transactionId;
    }

    @Data
    public static class EstimateFeeRequestDTO {
        @NotBlank(message = "Bitcoin address cannot be blank")
        private String address;

        @NotNull(message = "Amount in satoshis cannot be null")
        @JsonProperty("sats_amount")
        private Long satsAmount;

        @JsonProperty("target_conf")
        private Integer targetConf;
    }

    @Data
    public static class EstimateFeeResponseDTO {
        @JsonProperty("fee_sat")
        private long feeSat;

        @JsonProperty("sat_per_vbyte")
        private long satPerVbyte;
    }
}
