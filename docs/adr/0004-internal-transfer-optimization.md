# Internal transfer optimization

When a payment request's hash matches an Aratiri-issued invoice, the payment is settled as an internal transfer — debiting the sender and crediting the receiver locally — instead of routing through LND. The internal invoice is then cancelled in LND so it cannot be paid externally.

This was chosen because routing Aratiri-to-Aratiri payments through LND would add unnecessary Lightning network fees, latency, and failure modes. Internal settlement is instant and free. The LND invoice cancellation prevents double-spending.
