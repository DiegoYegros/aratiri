package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.configuration.OutboxProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class OutboxEventClaimer {

    static final Set<OutboxPublishStatus> CLAIMABLE_STATUSES =
            Set.of(OutboxPublishStatus.PENDING, OutboxPublishStatus.FAILED);

    private static final String LOCKED_BY_PREFIX = "outbox-";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;

    @Transactional
    List<OutboxEventEntity> claimBatch() {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusSeconds(outboxProperties.getLeaseSeconds());
        String lockedBy = LOCKED_BY_PREFIX + UUID.randomUUID();

        List<OutboxEventEntity> candidates = outboxEventRepository.lockClaimableEvents(
                now, outboxProperties.getBatchSize());
        for (OutboxEventEntity event : candidates) {
            event.claim(lockedBy, lockedUntil);
        }
        return candidates;
    }

    @Transactional
    int markPublished(OutboxEventEntity event) {
        return outboxEventRepository.markPublished(
                event.getId(),
                event.getLockedBy(),
                Instant.now(),
                OutboxPublishStatus.PUBLISHED,
                CLAIMABLE_STATUSES
        );
    }

    @Transactional
    int markPublishFailed(OutboxEventEntity event, String errorMessage) {
        Instant now = Instant.now();
        return outboxEventRepository.markPublishFailed(
                event.getId(),
                event.getLockedBy(),
                errorMessage,
                now.plus(RETRY_DELAY),
                OutboxPublishStatus.FAILED,
                CLAIMABLE_STATUSES
        );
    }

    @Transactional
    int markInvalid(OutboxEventEntity event, String errorMessage) {
        return outboxEventRepository.markInvalid(
                event.getId(),
                event.getLockedBy(),
                errorMessage,
                OutboxPublishStatus.INVALID,
                CLAIMABLE_STATUSES
        );
    }
}
