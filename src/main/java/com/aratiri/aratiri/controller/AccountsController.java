package com.aratiri.aratiri.controller;


import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.aratiri.service.AccountsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/accounts")
public class AccountsController {

    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @GetMapping("/account")
    public ResponseEntity<AccountDTO> getAccount(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(accountsService.getAccountByUserId(ctx.getUser().getId()));
    }

    @GetMapping("/account/{id}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable String id) {
        return ResponseEntity.ok(accountsService.getAccount(id));
    }

    @GetMapping("/account/user/{userId}")
    public ResponseEntity<AccountDTO> getAccountByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(accountsService.getAccountByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(@Validated @RequestBody CreateAccountRequestDTO request, @AratiriCtx AratiriContext ctx) {
        String userId = ctx.getUser().getId();
        return new ResponseEntity<>(accountsService.createAccount(request, userId), HttpStatus.CREATED);
    }
}