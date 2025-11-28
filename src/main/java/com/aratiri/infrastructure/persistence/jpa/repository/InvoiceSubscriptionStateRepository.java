package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceSubscriptionStateRepository extends JpaRepository<InvoiceSubscriptionState, String> {
}