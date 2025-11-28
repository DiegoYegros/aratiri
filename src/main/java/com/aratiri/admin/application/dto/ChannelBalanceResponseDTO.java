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
public class ChannelBalanceResponseDTO {

    @JsonProperty("local_balance")
    private AmountDTO localBalance;

    @JsonProperty("remote_balance")
    private AmountDTO remoteBalance;

}