package com.aratiri.transactions.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TransactionPageResponse {
    private List<TransactionDTOResponse> transactions;
    private String nextCursor;
    private boolean hasMore;
}
