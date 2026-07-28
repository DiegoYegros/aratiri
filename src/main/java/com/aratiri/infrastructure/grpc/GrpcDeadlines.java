package com.aratiri.infrastructure.grpc;

import java.time.Duration;

/**
 * Client-side gRPC deadlines for LND calls. Every request-path call must be bounded so a slow
 * LND cannot park HTTP request threads.
 */
public final class GrpcDeadlines {

    public static final Duration INVOICE_MUTATION = Duration.ofSeconds(15); // addInvoice
    public static final Duration LOOKUP = Duration.ofSeconds(5);            // decodePayReq, lookupInvoice, trackPayment first-read
    public static final Duration FEE_ESTIMATE = Duration.ofSeconds(10);     // estimateFee
    public static final Duration ONCHAIN_SEND = Duration.ofSeconds(30);     // sendCoins
    public static final Duration ADMIN = Duration.ofSeconds(30);            // getInfo, walletBalance, describeGraph, channels

    private GrpcDeadlines() {
    }
}
