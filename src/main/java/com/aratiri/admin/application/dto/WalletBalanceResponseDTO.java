package com.aratiri.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletBalanceResponseDTO {

    @JsonProperty("confirmed_balance")
    private long confirmedBalance;

    @JsonProperty("unconfirmed_balance")
    private long unconfirmedBalance;
}
