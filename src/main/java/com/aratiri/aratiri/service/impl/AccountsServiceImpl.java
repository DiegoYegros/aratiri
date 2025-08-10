package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.accounts.*;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.CurrencyConversionService;
import com.aratiri.aratiri.service.TransactionsService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final Logger logger = LoggerFactory.getLogger(AccountsServiceImpl.class);
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionsService transactionsService;
    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final AratiriProperties properties;
    private final CurrencyConversionService currencyConversionService;

    public AccountsServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, AccountRepository accountRepository, UserRepository userRepository, TransactionsService transactionsService, AratiriProperties properties, CurrencyConversionService currencyConversionService) {
        this.lightningStub = lightningStub;
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
        this.userRepository = userRepository;
        this.properties = properties;
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public AccountDTO getAccount(String id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND));
        return buildAccountDTO(account);
    }

    @Override
    public AccountDTO getAccountByUserId(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND));
        AccountEntity account = accountRepository.findByUser(user)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND));
        return buildAccountDTO(account);
    }

    @Override
    public boolean existsByAlias(String alias) {
        return accountRepository.existsByAlias(alias);
    }

    @Override
    public AccountDTO createAccount(CreateAccountRequestDTO request, String ctxUserId) {
        logger.info("Creating an account for userId [{}]", ctxUserId);
        String userId = request.getUserId();
        if (!userId.equalsIgnoreCase(ctxUserId)) {
            throw new AratiriException("UserId does not match logged-in user");
        }
        List<AccountEntity> accountList = accountRepository.getByUser_Id(userId);
        if (!accountList.isEmpty()) {
            throw new AratiriException("An account already exists for the user. Multiple accounts is not allowed.", HttpStatus.BAD_REQUEST);
        }
        UserEntity userEntity = userRepository.getReferenceById(userId);
        NewAddressRequest build = NewAddressRequest.newBuilder()
                .setType(AddressType.TAPROOT_PUBKEY).build();
        NewAddressResponse newAddressResponse = lightningStub.newAddress(build);
        String bitcoinAddress = newAddressResponse.getAddress();
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setBalance(0);
        accountEntity.setUser(userEntity);
        accountEntity.setBitcoinAddress(bitcoinAddress);
        String alias = request.getAlias();
        if (alias == null || alias.isEmpty()) {
            do {
                alias = AliasGenerator.generateAlias();
            } while (accountRepository.existsByAlias(alias));
        } else {
            if (accountRepository.existsByAlias(alias)) {
                throw new AratiriException("Alias is already in use.", HttpStatus.BAD_REQUEST);
            }
        }
        accountEntity.setAlias(alias);
        logger.info("saving the account entity. [{}]", accountEntity);
        AccountEntity save = accountRepository.save(accountEntity);
        logger.info("Saved the account.");
        return buildAccountDTO(save);
    }

    @Override
    public AccountDTO creditBalance(String userId, long satsAmount) {
        AccountEntity accountEntity = accountRepository.getByUser_Id(userId).getFirst();
        long balance = accountEntity.getBalance();
        long newBalance = balance + satsAmount;
        accountEntity.setBalance(newBalance);
        AccountEntity saved = accountRepository.save(accountEntity);
        return buildAccountDTO(saved);
    }

    @Override
    public AccountDTO getAccountByAlias(String alias) {
        Optional<AccountEntity> byAlias = accountRepository.findByAlias(alias);
        if (byAlias.isEmpty()) {
            throw new AratiriException("Account does not exist for given alias.", HttpStatus.NOT_FOUND);
        }
        AccountEntity accountEntity = byAlias.get();
        return buildAccountDTO(accountEntity);
    }

    @Override
    public List<AccountTransactionDTO> getTransactions(LocalDate from, LocalDate to, String userId) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
        List<TransactionDTOResponse> transactions = transactionsService.getTransactions(fromInstant, toInstant, userId);
        return transactions.stream()
                .map(t -> {
                    long satoshis = t.getAmount().multiply(BitcoinConstants.SATOSHIS_PER_BTC).longValue();
                    BigDecimal amountInBtc = new BigDecimal(satoshis).divide(BitcoinConstants.SATOSHIS_PER_BTC, 8, RoundingMode.HALF_UP);
                    Map<String, BigDecimal> btcPrices = currencyConversionService.getCurrentBtcPrice();
                    Map<String, BigDecimal> fiatEquivalents = btcPrices.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> amountInBtc.multiply(entry.getValue())
                            ));
                    AccountTransactionType accountTransactionType;
                    if (t.getType() == TransactionType.ONCHAIN_DEPOSIT || t.getType().name().toLowerCase().contains("credit")) {
                        accountTransactionType = AccountTransactionType.CREDIT;
                    } else {
                        accountTransactionType = AccountTransactionType.DEBIT;
                    }
                    return AccountTransactionDTO.builder()
                            .id(t.getId())
                            .date(t.getCreatedAt())
                            .amount(satoshis)
                            .status(AccountTransactionStatus.valueOf(t.getStatus().name()))
                            .type(accountTransactionType)
                            .fiatEquivalents(fiatEquivalents)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String buildLnurlForAlias(String alias) {
        String url = properties.getAratiriBaseUrl() + "/.well-known/lnurlp/" + alias;
        return LnurlBech32Util.encodeLnurl(url);
    }

    private String buildAlias(String alias) {
        String aratiriBaseUrl = properties.getAratiriBaseUrl()
                .replaceFirst("^https?://", "");
        return alias + "@" + aratiriBaseUrl;
    }

    private AccountDTO buildAccountDTO(AccountEntity accountEntity) {
        String lnurl = buildLnurlForAlias(accountEntity.getAlias());
        String alias = buildAlias(accountEntity.getAlias());
        String lnurlQrCode = QrCodeUtil.generateQrCodeBase64(lnurl);
        String bitcoinAddressQrCode = QrCodeUtil.generateQrCodeBase64(
                "bitcoin:" + accountEntity.getBitcoinAddress()
        );
        BigDecimal balanceInBtc = BitcoinConstants.satoshisToBtc(accountEntity.getBalance());
        Map<String, BigDecimal> btcPrices = currencyConversionService.getCurrentBtcPrice();
        Map<String, BigDecimal> fiatEquivalents = btcPrices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> balanceInBtc.multiply(entry.getValue())
                ));
        return AccountDTO.builder()
                .id(accountEntity.getId())
                .bitcoinAddress(accountEntity.getBitcoinAddress())
                .balance(accountEntity.getBalance())
                .userId(accountEntity.getUser().getId())
                .alias(alias)
                .lnurl(lnurl)
                .lnurlQrCode(lnurlQrCode)
                .bitcoinAddressQrCode(bitcoinAddressQrCode)
                .fiatEquivalents(fiatEquivalents)
                .build();
    }
}
