package com.aratiri.transactions;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionPageResponse;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Allows for transaction confirmation and settlement")
public class TransactionsAPI {

    private final TransactionsPort transactionsPort;

    public TransactionsAPI(TransactionsPort transactionsPort) {
        this.transactionsPort = transactionsPort;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get transaction by ID",
            description = "Retrieves a single transaction by its ID. Only returns the transaction if it belongs to the authenticated user."
    )
    public ResponseEntity<TransactionDTOResponse> getTransaction(
            @PathVariable("id") String id,
            @AratiriCtx AratiriContext aratiriContext
    ) {
        String userId = aratiriContext.user().getId();
        Optional<TransactionDTOResponse> transaction = transactionsPort.getTransactionById(id, userId);
        return transaction.map(ResponseEntity::ok)
                .orElseThrow(() -> new AratiriException("Transaction not found", HttpStatus.NOT_FOUND.value()));
    }

    @GetMapping
    @Operation(
            summary = "List transactions with cursor pagination",
            description = "Returns a paginated list of transactions for the authenticated user. Uses cursor-based pagination for stable, non-overlapping pages."
    )
    public ResponseEntity<TransactionPageResponse> listTransactions(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @AratiriCtx AratiriContext aratiriContext
    ) {
        if (cursor != null && !cursor.isEmpty()) {
            validateCursor(cursor);
        }
        String userId = aratiriContext.user().getId();
        TransactionPageResponse page = transactionsPort.getTransactionsWithCursor(userId, cursor, limit);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Operation(
            summary = "Confirm a pending transaction",
            description = "Confirms a pending transaction. This endpoint is restricted to admin/internal authority during the deprecation period. Clients must not use it for payment completion."
    )
    public ResponseEntity<TransactionDTOResponse> confirmTransaction(
            @PathVariable("id") String id
    ) {
        return ResponseEntity.ok(transactionsPort.confirmTransactionAsAdmin(id));
    }

    private void validateCursor(String cursor) {
        try {
            String[] parts = cursor.split("_", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new AratiriException("Invalid cursor format. Expected 'timestamp_id'", HttpStatus.BAD_REQUEST.value());
            }
            Long.parseLong(parts[0]);
        } catch (NumberFormatException _) {
            throw new AratiriException("Invalid cursor format: timestamp must be a valid number", HttpStatus.BAD_REQUEST.value());
        }
    }
}
