package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NodeOperationRetryPolicy {

  private final NodeOperationProperties nodeOperationProperties;

  boolean shouldFailLightningBeforeSend(NodeOperationEntity op) {
    return op.getAttemptCount() > lightningMaxAttempts();
  }

  String lightningMaxAttemptsFailureMessage() {
    return "Max lightning attempts (" + lightningMaxAttempts() + ") reached";
  }

  NodeOperationRetryDecision classifyAmbiguousLightningOutcome(NodeOperationEntity op, String message) {
    if (op.getAttemptCount() >= lightningMaxAttempts()) {
      return NodeOperationRetryDecision.unknownOutcome(message + " after max attempts");
    }
    return NodeOperationRetryDecision.retryable(message);
  }

  boolean shouldStopOnChainBeforeSend(NodeOperationEntity op) {
    return op.getAttemptCount() > onChainMaxAttempts();
  }

  String onChainMaxAttemptsUnknownOutcomeMessage() {
    return "Max on-chain attempts (" + onChainMaxAttempts()
        + ") reached without a recorded broadcast transaction id";
  }

  private int lightningMaxAttempts() {
    return nodeOperationProperties.getLightningMaxAttempts();
  }

  private int onChainMaxAttempts() {
    return nodeOperationProperties.getOnchainMaxAttempts();
  }
}
