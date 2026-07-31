package com.aratiri.payments.application.command;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

public record PaymentCommandFailurePayload(String message, Integer status) {

    public static PaymentCommandFailurePayload from(Throwable failure) {
        if (failure instanceof ApplicationException applicationException) {
            return new PaymentCommandFailurePayload(applicationException.getMessage(), applicationException.getStatus());
        }
        return new PaymentCommandFailurePayload("Payment command failed", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public ApplicationException toException() {
        return new ApplicationException(message, status);
    }
}
