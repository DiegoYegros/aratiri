package com.aratiri.payments.application.command;

import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;

public record PaymentCommandFailurePayload(String message, Integer status) {

    public static PaymentCommandFailurePayload from(Throwable failure) {
        if (failure instanceof AratiriException aratiriException) {
            return new PaymentCommandFailurePayload(aratiriException.getMessage(), aratiriException.getStatus());
        }
        return new PaymentCommandFailurePayload("Payment command failed", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public AratiriException toException() {
        return new AratiriException(message, status);
    }
}
