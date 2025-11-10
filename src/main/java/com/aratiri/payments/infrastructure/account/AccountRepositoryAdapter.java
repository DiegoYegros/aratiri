package com.aratiri.payments.infrastructure.account;

import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.payments.application.port.out.AccountsPort;
import com.aratiri.payments.domain.PaymentAccount;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component("paymentsAccountRepositoryAdapter")
public class AccountRepositoryAdapter implements AccountsPort {

    private final AccountRepository accountRepository;
    private final AccountLedgerService accountLedgerService;

    public AccountRepositoryAdapter(AccountRepository accountRepository, AccountLedgerService accountLedgerService) {
        this.accountRepository = accountRepository;
        this.accountLedgerService = accountLedgerService;
    }

    @Override
    public PaymentAccount getAccount(String userId) {
        var account = accountRepository.findByUserId(userId);
        if (account == null) {
            throw new AratiriException("Account not found", HttpStatus.NOT_FOUND.value());
        }
        long balance = accountLedgerService.getCurrentBalanceForAccount(account.getId());
        return new PaymentAccount(account.getUser().getId(), balance, account.getBitcoinAddress());
    }
}