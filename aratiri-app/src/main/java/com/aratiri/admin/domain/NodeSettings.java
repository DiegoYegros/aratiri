package com.aratiri.admin.domain;

import java.time.Instant;

public record NodeSettings(boolean autoManagePeers, Instant createdAt, Instant updatedAt) {
}
