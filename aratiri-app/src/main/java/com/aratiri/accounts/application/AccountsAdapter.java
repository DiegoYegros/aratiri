package com.aratiri.accounts.application;

import com.aratiri.accounts.application.dto.*;
import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.accounts.application.port.out.*;
import com.aratiri.accounts.domain.Account;
import com.aratiri.accounts.domain.AccountUser;
import com.aratiri.accounts.infrastructure.alias.AliasGenerator;
import com.aratiri.accounts.infrastructure.qr.QrCodeUtil;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.shared.constants.BitcoinConstants;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.shared.util.Bech32Util;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
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
import java.util.stream.Collectors;

@Service
public class AccountsAdapter implements AccountsPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountPersistencePort accountPersistencePort;
    private final LoadUserPort loadUserPort;
    private final TransactionsPort transactionsPort;
    private final LightningAddressPort lightningAddressPort;
    private final AratiriProperties properties;
    private final CurrencyConversionPort currencyConversionPort;
    private final AccountLedgerService accountLedgerService;

    public AccountsAdapter(
            AccountPersistencePort accountPersistencePort,
            LoadUserPort loadUserPort,
            TransactionsPort transactionsPort,
            LightningAddressPort lightningAddressPort,
            AratiriProperties properties,
            CurrencyConversionPort currencyConversionPort,
            AccountLedgerService accountLedgerService
    ) {
        this.accountPersistencePort = accountPersistencePort;
        this.loadUserPort = loadUserPort;
        this.transactionsPort = transactionsPort;
        this.lightningAddressPort = lightningAddressPort;
        this.properties = properties;
        this.currencyConversionPort = currencyConversionPort;
        this.accountLedgerService = accountLedgerService;
    }

    @Override
    public AccountDTO getAccount(String id) {
        Account account = accountPersistencePort.findById(id)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND.value()));
        return buildAccountDTO(account);
    }

    @Override
    public AccountDTO getAccountByUserId(String userId) {
        Account account = accountPersistencePort.findByUserId(userId)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND.value()));
        return buildAccountDTO(account);
    }

    @Override
    public boolean existsByAlias(String alias) {
        return accountPersistencePort.existsByAlias(alias);
    }

    @Override
    public AccountDTO createAccount(CreateAccountRequestDTO request, String ctxUserId) {
        logger.info("Creating an account for userId [{}]", ctxUserId);
        String userId = request.getUserId();
        if (!userId.equalsIgnoreCase(ctxUserId)) {
            throw new AratiriException("UserId does not match logged-in user");
        }
        if (accountPersistencePort.findByUserId(userId).isPresent()) {
            throw new AratiriException(
                    "An account already exists for the user. Multiple accounts is not allowed.",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        AccountUser user = loadUserPort.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND.value()));

        String bitcoinAddress = lightningAddressPort.generateTaprootAddress();
        String alias = determineAlias(request.getAlias());

        Account account = new Account(null, user, 0L, bitcoinAddress, alias);
        Account saved = accountPersistencePort.save(account);
        logger.info("Saved account [{}] for user [{}]", saved.id(), userId);
        return buildAccountDTO(saved);
    }

    @Override
    public AccountDTO creditBalance(String userId, long satsAmount) {
        Account account = accountPersistencePort.findByUserId(userId)
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND.value()));
        accountLedgerService.appendEntry(account.id(), null, satsAmount, AccountEntryType.MANUAL_ADJUSTMENT, "Manual credit adjustment");
        Account refreshed = accountPersistencePort.findById(account.id())
                .orElseThrow(() -> new AratiriException("Account not found for user", HttpStatus.NOT_FOUND.value()));
        return buildAccountDTO(refreshed);
    }

    @Override
    public AccountDTO getAccountByAlias(String alias) {
        Account account = accountPersistencePort.findByAlias(alias)
                .orElseThrow(() -> new AratiriException("Account does not exist for given alias.", HttpStatus.NOT_FOUND.value()));
        return buildAccountDTO(account);
    }

    @Override
    public List<AccountTransactionDTO> getTransactions(LocalDate from, LocalDate to, String userId) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
        List<TransactionDTOResponse> transactions = transactionsPort.getTransactions(fromInstant, toInstant, userId);
        return transactions.stream()
                .filter(e -> e.getStatus() != TransactionStatus.FAILED)
                .map(t -> {
                    long satoshis = t.getAmountSat();
                    BigDecimal amountInBtc = BitcoinConstants.satoshisToBtc(satoshis);
                    Map<String, BigDecimal> btcPrices = currencyConversionPort.getCurrentBtcPrice();
                    Map<String, BigDecimal> fiatEquivalents = btcPrices.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> amountInBtc.multiply(entry.getValue())
                            ));
                    AccountTransactionType accountTransactionType;
                    if (t.getType() == TransactionType.ONCHAIN_CREDIT || t.getType().name().toLowerCase().contains("credit")) {
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

    private String determineAlias(String requestedAlias) {
        String alias = requestedAlias;
        if (alias == null || alias.isBlank()) {
            do {
                alias = AliasGenerator.generateAlias();
            } while (accountPersistencePort.existsByAlias(alias));
        } else if (accountPersistencePort.existsByAlias(alias)) {
            throw new AratiriException("Alias is already in use.", HttpStatus.BAD_REQUEST.value());
        }
        return alias;
    }

    private String buildLnurlForAlias(String alias) {
        String url = properties.getAratiriBaseUrl() + "/.well-known/lnurlp/" + alias;
        return Bech32Util.encodeLnurl(url);
    }

    private String buildAlias(String alias) {
        String aratiriBaseUrl = properties.getAratiriBaseUrl()
                .replaceFirst("^https?://", "");
        return alias + "@" + aratiriBaseUrl;
    }

    private AccountDTO buildAccountDTO(Account account) {
        String lnurl = buildLnurlForAlias(account.alias());
        String alias = buildAlias(account.alias());
        String lnurlQrCode = QrCodeUtil.generateQrCodeBase64(lnurl);
        String bitcoinAddressQrCode = QrCodeUtil.generateQrCodeBase64("bitcoin:" + account.bitcoinAddress());
        BigDecimal balanceInBtc = BitcoinConstants.satoshisToBtc(account.balance());
        Map<String, BigDecimal> btcPrices = currencyConversionPort.getCurrentBtcPrice();
        Map<String, BigDecimal> fiatEquivalents = btcPrices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> balanceInBtc.multiply(entry.getValue())
                ));
        return AccountDTO.builder()
                .id(account.id())
                .bitcoinAddress(account.bitcoinAddress())
                .balance(account.balance())
                .userId(account.user().id())
                .alias(alias)
                .lnurl(lnurl)
                .lnurlQrCode(lnurlQrCode)
                .bitcoinAddressQrCode(bitcoinAddressQrCode)
                .fiatEquivalents(fiatEquivalents)
                .build();
    }
}