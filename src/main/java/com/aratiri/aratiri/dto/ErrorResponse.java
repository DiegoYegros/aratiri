package com.aratiri.aratiri.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private int status;
    private String timestamp;

    public ErrorResponse(String message, int status) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now().toString();
    }
}
