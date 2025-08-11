package com.aratiri.aratiri.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@RequiredArgsConstructor
public class AratiriException extends RuntimeException {
    private String code;
    private String message;
    private HttpStatus httpStatus;

    public AratiriException(String message) {
        this.message = message;
    }

    public AratiriException(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }
}