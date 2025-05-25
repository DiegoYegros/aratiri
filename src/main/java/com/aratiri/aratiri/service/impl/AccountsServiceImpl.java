package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
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
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Account not found for user"));
        return new AccountDTO(account.getId(), account.getBitcoinAddress(), account.getBalance(), account.getUser().getId());
    }

    @Override
    public AccountDTO getAccountByUserId(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found"));

        AccountEntity account = accountRepository.findByUser(user)
                .orElseThrow(() -> new AratiriException("Account not found for user"));

        return new AccountDTO(account.getId(), account.getBitcoinAddress(), account.getBalance(), account.getUser().getId());
    }
}