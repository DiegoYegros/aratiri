package com.aratiri.invoices.domain;

import java.time.Instant;
import java.util.List;

public record DecodedLightningInvoice(
        String paymentHash,
        long numSatoshis,
        String description,
        String descriptionHash,
        long expiry,
        String destination,
        long cltvExpiry,
        String paymentAddress,
        Instant timestamp,
        String fallbackAddress,
        List<String> blindedPaths
) {
}
