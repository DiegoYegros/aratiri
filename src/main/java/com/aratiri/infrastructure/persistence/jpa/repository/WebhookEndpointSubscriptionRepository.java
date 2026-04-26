package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEndpointSubscriptionRepository extends JpaRepository<WebhookEndpointSubscriptionEntity, String> {
}
