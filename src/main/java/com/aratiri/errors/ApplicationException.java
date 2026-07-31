package com.aratiri.errors;

import lombok.Getter;

/**
 * Transport-neutral application failure. Optional {@code status} is an HTTP status
 * code hint for the web adapter and for async payment-command failure round-trips
 * ({@code PaymentCommandFailurePayload}); it is not a Spring or servlet type.
 *
 * <p>Prefer context-owned semantic exceptions under {@code {context}.domain.exception}
 * for new failures; map those at the HTTP edge. Use this type where a shared catch
 * point or status-preserving async failure path is still required.
 */
@Getter
public class ApplicationException extends RuntimeException {
  private final Integer status;

  public ApplicationException(String message) {
    this(message, null);
  }

  public ApplicationException(String message, Integer status) {
    super(message);
    this.status = status;
  }

  public ApplicationException(String message, Integer status, Throwable cause) {
    super(message, cause);
    this.status = status;
  }
}
