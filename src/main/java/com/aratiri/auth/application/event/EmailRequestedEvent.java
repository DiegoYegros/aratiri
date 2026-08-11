package com.aratiri.auth.application.event;

/**
 * Kafka record for {@code email.requested}. Keyed by {@code emailType:to} so a single partition
 * preserves per-recipient ordering.
 */
public record EmailRequestedEvent(EmailType emailType, String to, String code) {

    public enum EmailType {
        VERIFICATION,
        PASSWORD_RESET
    }
}
