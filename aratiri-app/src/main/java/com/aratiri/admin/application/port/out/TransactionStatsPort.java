package com.aratiri.admin.application.port.out;

import com.aratiri.admin.application.dto.TransactionStatsDTO;

import java.time.Instant;
import java.util.List;

public interface TransactionStatsPort {

    List<TransactionStatsDTO> findTransactionStats(Instant from, Instant to);
}
