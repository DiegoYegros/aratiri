package com.aratiri.admin.infrastructure.persistence;

import com.aratiri.admin.application.port.out.TransactionStatsPort;
import com.aratiri.admin.application.dto.TransactionStatsDTO;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TransactionStatsAdapter implements TransactionStatsPort {

    private final TransactionsRepository transactionsRepository;

    public TransactionStatsAdapter(TransactionsRepository transactionsRepository) {
        this.transactionsRepository = transactionsRepository;
    }

    @Override
    public List<TransactionStatsDTO> findTransactionStats(Instant from, Instant to) {
        return transactionsRepository.findTransactionStats(from, to);
    }
}
