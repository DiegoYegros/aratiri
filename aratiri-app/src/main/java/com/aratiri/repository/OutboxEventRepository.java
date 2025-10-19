package com.aratiri.repository;

import com.aratiri.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findAndLockByProcessedAtIsNullOrderByCreatedAtAsc();
}