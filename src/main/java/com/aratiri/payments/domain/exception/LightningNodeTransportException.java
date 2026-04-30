package com.aratiri.payments.domain.exception;

public class LightningNodeTransportException extends RuntimeException {

  public LightningNodeTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
