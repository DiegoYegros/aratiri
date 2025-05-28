package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.AuthService;
import com.aratiri.aratiri.utils.AliasGenerator;
import com.aratiri.aratiri.utils.LnurlBech32Util;
import com.aratiri.aratiri.utils.QrCodeUtil;
import lnrpc.AddressType;
import lnrpc.LightningGrpc;
import lnrpc.NewAddressRequest;
import lnrpc.NewAddressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final Logger logger = LoggerFactory.getLogger(AccountsServiceImpl.class);
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final AratiriProperties properties;

    public AccountsServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, AccountRepository accountRepository, UserRepository userRepository, AratiriProperties properties) {
        this.lightningStub = lightningStub;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    public AccountDTO getAccount(String id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND));
        String lnurl = buildLnurlForAlias(account.getAlias());
        return AccountDTO.builder()
                .id(account.getId())
                .bitcoinAddress(account.getBitcoinAddress())
                .balance(account.getBalance())
                .userId(account.getUser().getId())
                .alias(account.getAlias())
                .lnurl(lnurl)
                .qrCode(QrCodeUtil.generateQrCodeBase64(lnurl))
                .build();
    }

    @Override
    public AccountDTO getAccountByUserId(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND));

        AccountEntity account = accountRepository.findByUser(user)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND));
        String lnurl = buildLnurlForAlias(account.getAlias());
        return AccountDTO.builder()
                .id(account.getId())
                .bitcoinAddress(account.getBitcoinAddress())
                .balance(account.getBalance())
                .userId(account.getUser().getId())
                .alias(account.getAlias())
                .lnurl(lnurl)
                .qrCode(QrCodeUtil.generateQrCodeBase64(lnurl))
                .build();
    }

    @Override
    public boolean existsByAlias(String alias) {
        return accountRepository.existsByAlias(alias);
    }

    @Override
    public AccountDTO createAccount(CreateAccountRequestDTO request, String ctxUserId) {
        String userId = request.getUserId();
        if (!userId.equalsIgnoreCase(ctxUserId)) {
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
        String alias;
        do {
            alias = AliasGenerator.generateAlias();
        } while (accountRepository.existsByAlias(alias));
        accountEntity.setAlias(alias);
        AccountEntity save = accountRepository.save(accountEntity);
        String lnurl = buildLnurlForAlias(save.getAlias());
        return AccountDTO.builder()
                .id(save.getId())
                .bitcoinAddress(save.getBitcoinAddress())
                .balance(save.getBalance())
                .userId(save.getUser().getId())
                .alias(save.getAlias())
                .lnurl(lnurl)
                .qrCode(QrCodeUtil.generateQrCodeBase64(lnurl))
                .build();
    }

    @Override
    public AccountDTO creditBalance(String userId, long satsAmount) {
        AccountEntity accountEntity = accountRepository.getByUser_Id(userId).getFirst();
        long balance = accountEntity.getBalance();
        long newBalance = balance + satsAmount;
        accountEntity.setBalance(newBalance);
        AccountEntity saved = accountRepository.save(accountEntity);
        String lnurl = buildLnurlForAlias(saved.getAlias());
        return AccountDTO.builder()
                .id(saved.getId())
                .bitcoinAddress(saved.getBitcoinAddress())
                .balance(saved.getBalance())
                .userId(saved.getUser().getId())
                .alias(saved.getAlias())
                .lnurl(lnurl)
                .qrCode(QrCodeUtil.generateQrCodeBase64(lnurl))
                .build();
    }

    @Override
    public AccountDTO getAccountByAlias(String alias) {
        Optional<AccountEntity> byAlias = accountRepository.findByAlias(alias);
        if (byAlias.isEmpty()) {
            throw new AratiriException("Account does not exist for given alias.", HttpStatus.NOT_FOUND);
        }
        AccountEntity accountEntity = byAlias.get();
        String lnurl = buildLnurlForAlias(accountEntity.getAlias());
        return AccountDTO.builder()
                .id(accountEntity.getId())
                .bitcoinAddress(accountEntity.getBitcoinAddress())
                .balance(accountEntity.getBalance())
                .userId(accountEntity.getUser().getId())
                .alias(accountEntity.getAlias())
                .lnurl(lnurl)
                .qrCode(QrCodeUtil.generateQrCodeBase64(lnurl))
                .build();
    }

    private String buildLnurlForAlias(String alias) {
        String url = properties.getAratiriBaseUrl() + "/.well-known/lnurlp/" + alias;
        return LnurlBech32Util.encodeLnurl(url);
    }
}