package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, UUID> {

    @Query("SELECT e FROM WebhookEndpointEntity e LEFT JOIN FETCH e.subscriptions WHERE e.enabled = true")
    List<WebhookEndpointEntity> findAllEnabledWithSubscriptions();
}
