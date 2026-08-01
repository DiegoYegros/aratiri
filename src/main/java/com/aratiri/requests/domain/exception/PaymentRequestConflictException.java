package com.aratiri.requests.domain.exception;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

public class PaymentRequestConflictException extends ApplicationException {

    public PaymentRequestConflictException(String message) {
        super(message, HttpStatus.CONFLICT.value());
    }
}
