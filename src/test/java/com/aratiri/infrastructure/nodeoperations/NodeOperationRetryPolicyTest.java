package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeOperationRetryPolicyTest {

  private NodeOperationRetryPolicy retryPolicy;

  @BeforeEach
  void setUp() {
    NodeOperationProperties properties = new NodeOperationProperties();
    properties.setLightningMaxAttempts(5);
    properties.setOnchainMaxAttempts(3);
    retryPolicy = new NodeOperationRetryPolicy(properties);
  }

  @Test
  void shouldFailLightningBeforeSend_onlyAfterMaxAttempts() {
    assertFalse(retryPolicy.shouldFailLightningBeforeSend(operationWithAttempts(5)));
    assertTrue(retryPolicy.shouldFailLightningBeforeSend(operationWithAttempts(6)));
    assertEquals("Max lightning attempts (5) reached", retryPolicy.lightningMaxAttemptsFailureMessage());
  }

  @Test
  void classifyAmbiguousLightningOutcome_retriesBeforeMaxAndMarksUnknownAtMax() {
    NodeOperationRetryDecision retry = retryPolicy.classifyAmbiguousLightningOutcome(
        operationWithAttempts(4),
        "LND payment outcome is unknown: INITIATED"
    );
    assertFalse(retry.unknownOutcome());
    assertEquals("LND payment outcome is unknown: INITIATED", retry.message());

    NodeOperationRetryDecision unknown = retryPolicy.classifyAmbiguousLightningOutcome(
        operationWithAttempts(5),
        "LND payment returned no terminal result"
    );
    assertTrue(unknown.unknownOutcome());
    assertEquals("LND payment returned no terminal result after max attempts", unknown.message());
  }

  @Test
  void shouldStopOnChainBeforeSend_onlyAfterMaxAttempts() {
    assertFalse(retryPolicy.shouldStopOnChainBeforeSend(operationWithAttempts(3)));
    assertTrue(retryPolicy.shouldStopOnChainBeforeSend(operationWithAttempts(4)));
    assertEquals(
        "Max on-chain attempts (3) reached without a recorded broadcast transaction id",
        retryPolicy.onChainMaxAttemptsUnknownOutcomeMessage()
    );
  }

  private NodeOperationEntity operationWithAttempts(int attempts) {
    NodeOperationEntity operation = new NodeOperationEntity();
    operation.setAttemptCount(attempts);
    return operation;
  }
}
