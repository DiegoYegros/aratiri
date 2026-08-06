package com.aratiri.auth.domain;

import java.time.Instant;

/**
 * Opaque notification WebSocket ticket bound to a single user.
 * Purpose is notifications-ws only; never accepted as an HTTP Bearer credential.
 */
public record WsTicket(String id, String userId, Instant expiresAt, Instant issuedAt) {
}
