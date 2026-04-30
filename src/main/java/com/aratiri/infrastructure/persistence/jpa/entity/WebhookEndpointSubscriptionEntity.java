package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(
    name = "WEBHOOK_ENDPOINT_SUBSCRIPTIONS",
    uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint_id", "event_type"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEndpointSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpointEntity endpoint;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
}
