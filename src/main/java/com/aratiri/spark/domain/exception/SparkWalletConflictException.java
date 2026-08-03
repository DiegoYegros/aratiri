package com.aratiri.spark.domain.exception;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

public class SparkWalletConflictException extends ApplicationException {

    public SparkWalletConflictException(String message) {
        super(message, HttpStatus.CONFLICT.value());
    }
}
