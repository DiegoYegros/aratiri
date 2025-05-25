package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.entity.Account;
import com.aratiri.aratiri.entity.User;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AccountsService;
import org.springframework.stereotype.Service;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountsServiceImpl(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AccountDTO getAccount(String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found for user"));
        return new AccountDTO(account.getId(), account.getBitcoinAddress(), account.getBalance(), account.getUser().getId());
    }

    @Override
    public AccountDTO getAccountByUserId(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found for user"));

        return new AccountDTO(account.getId(), account.getBitcoinAddress(), account.getBalance(), account.getUser().getId());
    }
}