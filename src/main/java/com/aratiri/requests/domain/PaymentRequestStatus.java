package com.aratiri.requests.domain;

public enum PaymentRequestStatus {
    PROVISIONING,
    OPEN,
    CANCEL_PENDING,
    CANCELLED,
    PAID,
    FAILED,
    /** Derived only from OPEN when clockInstant >= expiresAt; never stored. */
    EXPIRED
}
