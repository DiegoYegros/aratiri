package com.aratiri.transactions.application;

public record LightningRoutingFeeSettlement(
        String transactionId,
        long feeSat
) {
}
