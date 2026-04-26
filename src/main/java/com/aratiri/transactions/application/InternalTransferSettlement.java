package com.aratiri.transactions.application;

public record InternalTransferSettlement(
        String transactionId,
        String senderId,
        String receiverId,
        long amountSat,
        String paymentHash
) {
}
