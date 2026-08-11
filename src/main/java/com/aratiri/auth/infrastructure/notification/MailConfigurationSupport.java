package com.aratiri.auth.infrastructure.notification;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Single source of truth for the "is SMTP configured" check shared by the request-side publisher
 * (fail-fast 503 before an email is queued) and the consumer-side sender (defense in depth at
 * send time).
 */
final class MailConfigurationSupport {

    private MailConfigurationSupport() {}

    static void requireConfigured(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new ApplicationException(
                    "Email delivery is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
