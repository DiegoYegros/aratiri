package com.aratiri.admin.application.port.out;

import com.aratiri.dto.admin.TransactionStatsDTO;

import java.time.Instant;
import java.util.List;

public interface TransactionStatsPort {

    List<TransactionStatsDTO> findTransactionStats(Instant from, Instant to);
}
