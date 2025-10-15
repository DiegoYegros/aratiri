package com.aratiri.service;

import com.aratiri.dto.accounts.AccountDTO;
import com.aratiri.dto.accounts.AccountTransactionDTO;
import com.aratiri.dto.accounts.CreateAccountRequestDTO;

import java.time.LocalDate;
import java.util.List;

public interface AccountsService {

    AccountDTO getAccount(String id);

    AccountDTO getAccountByUserId(String userId);

    boolean existsByAlias(String alias);

    AccountDTO createAccount(CreateAccountRequestDTO request, String ctxUserId);

    AccountDTO creditBalance(String userId, long satsAmount);

    AccountDTO getAccountByAlias(String alias);

    List<AccountTransactionDTO> getTransactions(LocalDate from, LocalDate to, String userId);
}