package com.aratiri.payments.domain;

public record OnChainFeeEstimate(
        long feeSat,
        long satPerVbyte
) {
}
