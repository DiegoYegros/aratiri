package com.aratiri.aratiri.controller;


import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.service.AccountsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
public class AccountsController {

    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @GetMapping("/account/{id}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable String id) {
        return ResponseEntity.ok(accountsService.getAccount(id));
    }

    @GetMapping("/account/user/{userId}")
    public ResponseEntity<AccountDTO> getAccountByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(accountsService.getAccountByUserId(userId));
    }
}