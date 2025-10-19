package com.aratiri.payments.domain;

public record PaymentAccount(
        String userId,
        long balance,
        String bitcoinAddress
) {
}
