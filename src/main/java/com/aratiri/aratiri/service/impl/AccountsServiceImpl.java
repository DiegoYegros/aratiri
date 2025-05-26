package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.AuthService;
import lnrpc.AddressType;
import lnrpc.LightningGrpc;
import lnrpc.NewAddressRequest;
import lnrpc.NewAddressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final Logger logger = LoggerFactory.getLogger(AccountsServiceImpl.class);
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final AuthService authService;

    public AccountsServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, AccountRepository accountRepository, UserRepository userRepository, AuthService authService) {
        this.lightningStub = lightningStub;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public AccountDTO getAccount(String id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Account not found for user"));
        return new AccountDTO(account.getId(), account.getBitcoinAddress(), account.getBalance(), account.getUser().getId());
    }

    @Override
    public AccountDTO getAccount() {
        String id = authService.getCurrentUser().getId();
        logger.info("Searching account for userId [{}]", id);
        AccountEntity account = accountRepository.findByUserId(id);
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

    @Override
    public AccountDTO createAccount(CreateAccountRequestDTO request) {
        String userId = request.getUserId();
        if (!userId.equalsIgnoreCase(authService.getCurrentUser().getId())) {
            throw new AratiriException("UserId does not match logged-in user");
        }
        List<AccountEntity> accountList = accountRepository.getByUser_Id(userId);
        if (!accountList.isEmpty()) {
            throw new AratiriException("An account already exists for the user. Multiple accounts is not allowed.", HttpStatus.BAD_REQUEST);
        }
        UserEntity userEntity = accountList.getFirst().getUser();
        NewAddressRequest build = NewAddressRequest.newBuilder()
                .setType(AddressType.TAPROOT_PUBKEY).build();
        NewAddressResponse newAddressResponse = lightningStub.newAddress(build);
        String bitcoinAddress = newAddressResponse.getAddress();
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setBalance(0);
        accountEntity.setUser(userEntity);
        accountEntity.setBitcoinAddress(bitcoinAddress);
        AccountEntity save = accountRepository.save(accountEntity);

        return new AccountDTO(save.getId(), save.getBitcoinAddress(), save.getBalance(), save.getUser().getId());
    }

    @Override
    public AccountDTO creditBalance(String userId, long satsAmount) {
        AccountEntity accountEntity = accountRepository.getByUser_Id(userId).getFirst();
        long balance = accountEntity.getBalance();
        long newBalance = balance + satsAmount;
        accountEntity.setBalance(newBalance);
        AccountEntity saved = accountRepository.save(accountEntity);
        return new AccountDTO(saved.getId(), saved.getBitcoinAddress(), saved.getBalance(), saved.getUser().getId());
    }

}