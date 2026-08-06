package com.aratiri.webhooks.application.destination;

import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import com.aratiri.infrastructure.http.destination.OutboundDestinationRejectedException;
import org.springframework.stereotype.Component;

/**
 * Webhook-facing adapter over {@link OutboundDestinationPolicy}. Maps shared rejections to
 * {@link WebhookDestinationRejectedException} so admin/delivery keep the webhook public message.
 */
@Component
public class WebhookDestinationPolicy {

  private final OutboundDestinationPolicy outboundDestinationPolicy;

  public WebhookDestinationPolicy(OutboundDestinationPolicy outboundDestinationPolicy) {
    this.outboundDestinationPolicy = outboundDestinationPolicy;
  }

  public void validate(String rawUrl) {
    try {
      outboundDestinationPolicy.validate(rawUrl);
    } catch (OutboundDestinationRejectedException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }
}
