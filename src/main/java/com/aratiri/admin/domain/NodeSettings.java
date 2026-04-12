package com.aratiri.admin.domain;

import java.time.Instant;

public record NodeSettings(
        boolean autoManagePeers,
        long transactionReconciliationMinAgeMs,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String SINGLETON_ID = "singleton";
    public static final long DEFAULT_TRANSACTION_RECONCILIATION_MIN_AGE_MS = 300_000L;
}
