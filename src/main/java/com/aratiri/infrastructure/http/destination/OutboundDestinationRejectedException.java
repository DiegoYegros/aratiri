package com.aratiri.infrastructure.http.destination;

/**
 * Fail-closed rejection of an outbound destination URL. Public message is stable and does not
 * expose resolution details or the full URL.
 */
public class OutboundDestinationRejectedException extends RuntimeException {

  public static final String PUBLIC_MESSAGE = "Outbound URL is not allowed";

  public OutboundDestinationRejectedException() {
    super(PUBLIC_MESSAGE);
  }

  public OutboundDestinationRejectedException(Throwable cause) {
    super(PUBLIC_MESSAGE, cause);
  }
}
