package com.aratiri.webhooks.application.destination;

/**
 * Fail-closed rejection of a webhook destination. Public message is stable and does not
 * expose resolution details or the full URL.
 */
public class WebhookDestinationRejectedException extends RuntimeException {

  public static final String PUBLIC_MESSAGE = "Webhook destination URL is not allowed";

  public WebhookDestinationRejectedException() {
    super(PUBLIC_MESSAGE);
  }

  public WebhookDestinationRejectedException(Throwable cause) {
    super(PUBLIC_MESSAGE, cause);
  }
}
