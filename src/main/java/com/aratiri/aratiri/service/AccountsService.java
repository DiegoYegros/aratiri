package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;

public interface AccountsService {
    AccountDTO getAccount();

    AccountDTO getAccount(String id);

    AccountDTO getAccountByUserId(String userId);

    boolean existsByAlias(String alias);

    AccountDTO createAccount(CreateAccountRequestDTO request);

    AccountDTO creditBalance(String userId, long satsAmount);
    AccountDTO getAccountByAlias(String alias);
}