package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.configuration.OutboxProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxProperties properties;
    private OutboxEventClaimer claimer;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setBatchSize(2);
        properties.setLeaseSeconds(30);
        claimer = new OutboxEventClaimer(outboxEventRepository, properties);
    }

    @Test
    void claimBatch_leasesClaimableEventsWithConfiguredBatchSize() {
        UUID id = UUID.randomUUID();
        OutboxEventEntity event = OutboxEventEntity.builder().id(id).build();
        when(outboxEventRepository.lockClaimableEvents(any(Instant.class), eq(2)))
                .thenReturn(List.of(event));

        List<OutboxEventEntity> claimed = claimer.claimBatch();

        assertEquals(1, claimed.size());
        assertTrue(claimed.getFirst().getLockedBy().startsWith("outbox-"));
        assertNotNull(claimed.getFirst().getLockedUntil());
        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxEventRepository).lockClaimableEvents(nowCaptor.capture(), eq(2));
        assertTrue(claimed.getFirst().getLockedUntil().isAfter(nowCaptor.getValue()));
    }

    @Test
    void claimBatch_emptyWhenNothingClaimable() {
        when(outboxEventRepository.lockClaimableEvents(any(Instant.class), eq(2)))
                .thenReturn(List.of());

        assertTrue(claimer.claimBatch().isEmpty());
    }

    @Test
    void markPublished_delegatesFencedUpdate() {
        OutboxEventEntity event = OutboxEventEntity.builder().id(UUID.randomUUID()).build();
        event.claim("outbox-token", Instant.now().plusSeconds(30));
        when(outboxEventRepository.markPublished(
                eq(event.getId()),
                eq("outbox-token"),
                any(Instant.class),
                eq(OutboxPublishStatus.PUBLISHED),
                eq(OutboxEventClaimer.CLAIMABLE_STATUSES)
        )).thenReturn(1);

        assertEquals(1, claimer.markPublished(event));
    }

    @Test
    void markPublishFailed_schedulesRetryOneSecondOut() {
        OutboxEventEntity event = OutboxEventEntity.builder().id(UUID.randomUUID()).build();
        event.claim("outbox-token", Instant.now().plusSeconds(30));
        ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
        when(outboxEventRepository.markPublishFailed(
                eq(event.getId()),
                eq("outbox-token"),
                eq("kafka down"),
                nextAttemptCaptor.capture(),
                eq(OutboxPublishStatus.FAILED),
                eq(OutboxEventClaimer.CLAIMABLE_STATUSES)
        )).thenReturn(1);

        Instant before = Instant.now();
        assertEquals(1, claimer.markPublishFailed(event, "kafka down"));
        Instant after = Instant.now();

        Instant nextAttempt = nextAttemptCaptor.getValue();
        assertFalse(nextAttempt.isBefore(before.plusSeconds(1)));
        assertFalse(nextAttempt.isAfter(after.plusSeconds(1)));
    }

    @Test
    void markInvalid_delegatesFencedUpdate() {
        OutboxEventEntity event = OutboxEventEntity.builder().id(UUID.randomUUID()).build();
        event.claim("outbox-token", Instant.now().plusSeconds(30));
        when(outboxEventRepository.markInvalid(
                eq(event.getId()),
                eq("outbox-token"),
                eq("bad type"),
                eq(OutboxPublishStatus.INVALID),
                eq(OutboxEventClaimer.CLAIMABLE_STATUSES)
        )).thenReturn(1);

        assertEquals(1, claimer.markInvalid(event, "bad type"));
    }
}
