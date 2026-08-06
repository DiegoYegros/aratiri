package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.consumer.NotificationConsumer;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventClaimerIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoBean
    private LightningListener lightningListener;

    @MockitoBean
    private OnChainTransactionListener onChainTransactionListener;

    @MockitoBean
    private NotificationConsumer notificationConsumer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventClaimer outboxEventClaimer;

    @Autowired
    private OutboxEventJob outboxEventJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Concurrent claimers lease disjoint event IDs")
    void concurrent_claimers_take_disjoint_rows() throws Exception {
        for (int i = 0; i < 20; i++) {
            outboxEventRepository.save(pendingEvent("concurrent-" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<OutboxEventEntity>> task = () -> outboxEventClaimer.claimBatch();
            Future<List<OutboxEventEntity>> first = executor.submit(task);
            Future<List<OutboxEventEntity>> second = executor.submit(task);

            List<OutboxEventEntity> claimedA = first.get();
            List<OutboxEventEntity> claimedB = second.get();

            Set<UUID> idsA = new HashSet<>();
            claimedA.forEach(e -> idsA.add(e.getId()));
            Set<UUID> idsB = new HashSet<>();
            claimedB.forEach(e -> idsB.add(e.getId()));

            assertTrue(idsA.stream().noneMatch(idsB::contains), "claimers must not double-lease the same row");
            assertEquals(idsA.size() + idsB.size(), claimedA.size() + claimedB.size());
            assertFalse(claimedA.isEmpty() && claimedB.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Active lease is excluded; expired lease is reclaimable")
    void active_lease_excluded_expired_lease_reclaimable() {
        OutboxEventEntity event = outboxEventRepository.save(pendingEvent("lease-lifecycle"));
        Instant future = Instant.now().plusSeconds(120);
        jdbcTemplate.update(
                "UPDATE aratiri.outbox_events SET locked_by = ?, locked_until = ? WHERE id = ?",
                "outbox-other", java.sql.Timestamp.from(future), event.getId()
        );

        assertTrue(outboxEventClaimer.claimBatch().isEmpty(), "active lease must exclude the row");

        Instant past = Instant.now().minusSeconds(5);
        jdbcTemplate.update(
                "UPDATE aratiri.outbox_events SET locked_by = ?, locked_until = ? WHERE id = ?",
                "outbox-stale", java.sql.Timestamp.from(past), event.getId()
        );

        List<OutboxEventEntity> reclaimed = outboxEventClaimer.claimBatch();
        assertEquals(1, reclaimed.size());
        assertEquals(event.getId(), reclaimed.getFirst().getId());
        assertTrue(reclaimed.getFirst().getLockedBy().startsWith("outbox-"));
        assertNotEquals("outbox-stale", reclaimed.getFirst().getLockedBy());
    }

    @Test
    @DisplayName("Stale fence after reclaim updates zero rows")
    void stale_fence_after_reclaim_updates_zero_rows() {
        OutboxEventEntity event = outboxEventRepository.save(pendingEvent("fence"));
        Instant past = Instant.now().minusSeconds(5);
        jdbcTemplate.update(
                "UPDATE aratiri.outbox_events SET locked_by = ?, locked_until = ? WHERE id = ?",
                "outbox-old", java.sql.Timestamp.from(past), event.getId()
        );

        List<OutboxEventEntity> reclaimed = outboxEventClaimer.claimBatch();
        assertEquals(1, reclaimed.size());
        String newToken = reclaimed.getFirst().getLockedBy();

        OutboxEventEntity staleView = OutboxEventEntity.builder()
                .id(event.getId())
                .aggregateType("PAYMENT")
                .aggregateId("fence")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{}")
                .build();
        staleView.claim("outbox-old", Instant.now().plusSeconds(30));

        assertEquals(0, outboxEventClaimer.markPublished(staleView));
        assertEquals(0, outboxEventClaimer.markPublishFailed(staleView, "stale"));

        assertEquals(1, outboxEventClaimer.markPublished(reclaimed.getFirst()));
        OutboxEventEntity published = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxPublishStatus.PUBLISHED, published.getPublishStatus());
        assertNull(published.getLockedBy());
        assertNull(published.getLockedUntil());
        assertEquals(newToken, reclaimed.getFirst().getLockedBy());
    }

    @Test
    @DisplayName("Job publishes pending event and clears lease fields")
    void job_publishes_and_clears_lease() {
        OutboxEventEntity event = outboxEventRepository.save(pendingEvent("job-publish"));

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxPublishStatus.PUBLISHED, processed.getPublishStatus());
        assertNotNull(processed.getProcessedAt());
        assertNull(processed.getLockedBy());
        assertNull(processed.getLockedUntil());
    }

    private static OutboxEventEntity pendingEvent(String aggregateId) {
        return OutboxEventEntity.builder()
                .aggregateType("PAYMENT")
                .aggregateId(aggregateId)
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"aggregateId\":\"" + aggregateId + "\"}")
                .build();
    }
}
