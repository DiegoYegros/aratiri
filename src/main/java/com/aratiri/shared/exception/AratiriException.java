package com.aratiri.shared.exception;

import lombok.Getter;

@Getter
public class AratiriException extends RuntimeException {
    private final String code;
    private final Integer status;

    public AratiriException(String message) {
        this(message, null);
    }

    public AratiriException(String message, Integer status) {
        super(message);
        this.code = null;
        this.status = status;
    }

    public AratiriException(String message, Integer status, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.status = status;
    }
}
