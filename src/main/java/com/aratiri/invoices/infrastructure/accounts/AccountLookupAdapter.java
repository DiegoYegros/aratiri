package com.aratiri.invoices.infrastructure.accounts;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.invoices.application.port.out.AccountLookupPort;
import org.springframework.stereotype.Component;

@Component
public class AccountLookupAdapter implements AccountLookupPort {

    private final AccountsPort accountsPort;

    public AccountLookupAdapter(AccountsPort accountsPort) {
        this.accountsPort = accountsPort;
    }

    @Override
    public String getUserIdByAlias(String alias) {
        return accountsPort.getAccountByAlias(alias).getUserId();
    }
}
