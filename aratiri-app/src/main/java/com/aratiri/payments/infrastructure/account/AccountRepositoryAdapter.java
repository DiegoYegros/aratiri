package com.aratiri.payments.infrastructure.account;

import com.aratiri.shared.exception.AratiriException;
import com.aratiri.payments.application.port.out.AccountsPort;
import com.aratiri.payments.domain.PaymentAccount;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component("paymentsAccountRepositoryAdapter")
public class AccountRepositoryAdapter implements AccountsPort {

    private final AccountRepository accountRepository;

    public AccountRepositoryAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public PaymentAccount getAccount(String userId) {
        var account = accountRepository.findByUserId(userId);
        if (account == null) {
            throw new AratiriException("Account not found", HttpStatus.NOT_FOUND);
        }
        return new PaymentAccount(account.getUser().getId(), account.getBalance(), account.getBitcoinAddress());
    }
}