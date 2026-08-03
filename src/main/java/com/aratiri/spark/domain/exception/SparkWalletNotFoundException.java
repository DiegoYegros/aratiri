package com.aratiri.spark.domain.exception;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

public class SparkWalletNotFoundException extends ApplicationException {

    public SparkWalletNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND.value());
    }
}
