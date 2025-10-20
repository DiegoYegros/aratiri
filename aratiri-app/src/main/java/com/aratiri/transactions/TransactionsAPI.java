package com.aratiri.transactions;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Allows for transaction confirmation and settlement")
public class TransactionsAPI {

    private final TransactionsPort transactionsPort;

    public TransactionsAPI(TransactionsPort transactionsPort) {
        this.transactionsPort = transactionsPort;
    }

    @PostMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm a pending transaction",
            description = "Confirms a pending transaction initiated by the user. This endpoint finalizes the transaction " +
                    "processing flow for Lightning or on-chain operations once the backend has completed settlement."
    )
    public ResponseEntity<TransactionDTOResponse> confirmTransaction(
            @PathVariable("id") String id,
            @AratiriCtx AratiriContext aratiriContext
    ) {
        String userId = aratiriContext.user().getId();
        return ResponseEntity.ok(transactionsPort.confirmTransaction(id, userId));
    }
}
