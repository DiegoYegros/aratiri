package com.aratiri.accounts.application.port.in;

import com.aratiri.accounts.application.dto.AccountDTO;
import com.aratiri.accounts.application.dto.AccountTransactionDTO;
import com.aratiri.accounts.application.dto.CreateAccountRequestDTO;

import java.time.LocalDate;
import java.util.List;

public interface AccountsPort {

    AccountDTO getAccount(String id);

    AccountDTO getAccountByUserId(String userId);

    boolean existsByAlias(String alias);

    AccountDTO createAccount(CreateAccountRequestDTO request, String ctxUserId);

    AccountDTO creditBalance(String userId, long satsAmount);

    AccountDTO getAccountByAlias(String alias);

    List<AccountTransactionDTO> getTransactions(LocalDate from, LocalDate to, String userId);
}