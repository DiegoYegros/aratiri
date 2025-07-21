package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByProcessedAtIsNullOrderByCreatedAtAsc();
}