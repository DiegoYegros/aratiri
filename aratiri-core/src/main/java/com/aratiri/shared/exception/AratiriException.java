package com.aratiri.shared.exception;

import lombok.Getter;

@Getter
public class AratiriException extends RuntimeException {
    private String code;
    private final Integer status;

    public AratiriException(String message) {
        this(message, null);
    }

    public AratiriException(String message, Integer status) {
        super(message);
        this.status = status;
    }
}