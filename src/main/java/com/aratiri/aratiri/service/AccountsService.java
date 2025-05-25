package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;

public interface AccountsService {

    AccountDTO getAccount(String id);
    AccountDTO getAccountByUserId(String userId);
    AccountDTO createAccount(CreateAccountRequestDTO request);
}