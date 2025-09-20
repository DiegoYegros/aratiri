package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.aratiri.service.TransactionsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Allows for transaction confirmation and settlement")
public class TransactionsController {

    private final TransactionsService transactionsService;

    public TransactionsController(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<TransactionDTOResponse> confirmTransaction(@PathVariable("id") String id, @AratiriCtx AratiriContext aratiriContext) {
        String userId = aratiriContext.user().getId();
        return ResponseEntity.ok(transactionsService.confirmTransaction(id, userId));
    }
}
