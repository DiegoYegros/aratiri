package com.aratiri.requests.domain.exception;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

public class PaymentRequestNotFoundException extends ApplicationException {

    public PaymentRequestNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND.value());
    }
}
