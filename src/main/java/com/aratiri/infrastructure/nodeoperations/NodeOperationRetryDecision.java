package com.aratiri.infrastructure.nodeoperations;

record NodeOperationRetryDecision(boolean unknownOutcome, String message) {

  static NodeOperationRetryDecision retryable(String message) {
    return new NodeOperationRetryDecision(false, message);
  }

  static NodeOperationRetryDecision unknownOutcome(String message) {
    return new NodeOperationRetryDecision(true, message);
  }
}
