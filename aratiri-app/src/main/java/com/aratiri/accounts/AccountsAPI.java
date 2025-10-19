package com.aratiri.accounts;


import com.aratiri.context.AratiriContext;
import com.aratiri.context.AratiriCtx;
import com.aratiri.dto.accounts.AccountDTO;
import com.aratiri.dto.accounts.AccountTransactionsDTOResponse;
import com.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.accounts.application.port.in.AccountsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts", description = "Bitcoin Lightning and on-chain account management operations")
public class AccountsAPI {

    private final AccountsPort accountsPort;

    public AccountsAPI(AccountsPort accountsPort) {
        this.accountsPort = accountsPort;
    }

    @GetMapping("/account")
    @Operation(
            summary = "Get current user's account",
            description = "Retrieves the Bitcoin Lightning and on-chain account information for the current user. " +
                    "This includes balance information, account settings, and payment routing configuration."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Account information retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountDTO.class)
                    )
            )
    })
    public ResponseEntity<AccountDTO> getAccount(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(accountsPort.getAccountByUserId(ctx.user().getId()));
    }

    @GetMapping("/account/{id}")
    @Operation(
            summary = "Get account by ID",
            description = "Retrieves a specific Bitcoin Lightning and on-chain account by its unique identifier. "
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Account information retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountDTO.class)
                    )
            )
    })
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable String id) {
        return ResponseEntity.ok(accountsPort.getAccount(id));
    }

    @GetMapping("/account/user/{userId}")
    @Operation(
            summary = "Get account by user ID",
            description = "Retrieves the Bitcoin Lightning and on-chain account associated with a specific user. "
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Account information retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountDTO.class)
                    )
            )
    })
    public ResponseEntity<AccountDTO> getAccountByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(accountsPort.getAccountByUserId(userId));
    }

    @PostMapping
    @Operation(
            summary = "Create new account",
            description = "Creates a new Bitcoin Lightning and on-chain account for the authenticated user. " +
                    "This sets up the necessary infrastructure for handling Lightning payments, on-chain transactions, " +
                    "and internal balance management. Each user can typically have only one account."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Account created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountDTO.class)
                    )
            )
    })
    public ResponseEntity<AccountDTO> createAccount(@Validated @RequestBody CreateAccountRequestDTO request, @AratiriCtx AratiriContext ctx) {
        String userId = ctx.user().getId();
        return new ResponseEntity<>(accountsPort.createAccount(request, userId), HttpStatus.CREATED);
    }

    @GetMapping("/account/transactions")
    @Operation(
            summary = "Get transactions"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountDTO.class)
                    )
            )
    })
    public ResponseEntity<AccountTransactionsDTOResponse> getTransactions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                          @AratiriCtx AratiriContext ctx) {
        String userId = ctx.user().getId();
        return new ResponseEntity<>(AccountTransactionsDTOResponse.builder().transactions(accountsPort.getTransactions(from, to, userId)).build(), HttpStatus.OK);
    }
}