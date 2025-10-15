package com.aratiri.repository;

import com.aratiri.entity.InvoiceSubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceSubscriptionStateRepository extends JpaRepository<InvoiceSubscriptionState, String> {
}