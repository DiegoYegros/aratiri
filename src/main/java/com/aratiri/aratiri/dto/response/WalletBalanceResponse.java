package com.aratiri.aratiri.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletBalanceResponse {
    long totalBalance;
}