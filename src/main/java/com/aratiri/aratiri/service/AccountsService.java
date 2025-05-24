package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.accounts.AccountDTO;

public interface AccountsService {

    AccountDTO getAccount(String id);
    AccountDTO getAccountByUserId(String userId);
}