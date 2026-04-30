# Ports-and-adapters architecture

Aratiri applies a ports-and-adapters (hexagonal) architecture. Each bounded context exposes `application.port.in` interfaces for incoming use cases and `application.port.out` interfaces for outgoing infrastructure. Most modules follow this pattern: API classes call port-in interfaces, application services call port-out interfaces, and infrastructure adapters implement those ports with JPA repositories, LND gRPC clients, Kafka, HTTP clients, or external APIs.

This was chosen over a layered architecture because LND gRPC and Kafka are heavyweight external dependencies that must be replaceable in tests and in staging environments. Ports-and-adapters lets each module swap infrastructure without touching domain logic.
