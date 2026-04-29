package com.aratiri.infrastructure.persistence.ledger;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.webhooks.application.AccountBalanceChangedWebhookFacts;
import com.aratiri.webhooks.application.WebhookEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLedgerService {

    private final AccountRepository accountRepository;
    private final AccountEntryRepository accountEntryRepository;
    private final WebhookEventService webhookEventService;

    public AccountLedgerService(AccountRepository accountRepository, AccountEntryRepository accountEntryRepository, WebhookEventService webhookEventService) {
        this.accountRepository = accountRepository;
        this.accountEntryRepository = accountEntryRepository;
        this.webhookEventService = webhookEventService;
    }

    @Transactional
    public long appendEntryForUser(TransactionEntity transaction, long deltaSats, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findByUserIdForUpdate(transaction.getUserId())
                .orElse(null);
        if (account == null) {
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        return appendEntry(account, transaction, deltaSats, entryType, description);
    }

    @Transactional
    public long appendLightningDebitSettlement(TransactionEntity transaction) {
        return appendExternalDebitSettlement(
                transaction,
                AccountEntryType.LIGHTNING_DEBIT,
                "Lightning payment sent"
        );
    }

    @Transactional
    public long appendLightningCreditSettlement(TransactionEntity transaction) {
        return appendIncomingCreditSettlement(
                transaction,
                AccountEntryType.LIGHTNING_CREDIT,
                "Lightning invoice settled"
        );
    }

    @Transactional
    public long appendOnChainDebitSettlement(TransactionEntity transaction) {
        return appendExternalDebitSettlement(
                transaction,
                AccountEntryType.ONCHAIN_DEBIT,
                "On-chain withdrawal"
        );
    }

    @Transactional
    public long appendOnChainCreditSettlement(TransactionEntity transaction) {
        return appendIncomingCreditSettlement(
                transaction,
                AccountEntryType.ONCHAIN_CREDIT,
                "On-chain deposit"
        );
    }

    @Transactional
    public long appendEntry(String accountId, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AratiriException("Account not found for id: " + accountId));
        return appendEntry(account, transactionId, deltaSats, entryType, description);
    }

    @Transactional(readOnly = true)
    public long getCurrentBalanceForAccount(String accountId) {
        return accountEntryRepository.findFirstByAccount_IdOrderByCreatedAtDescIdDesc(accountId)
                .map(AccountEntryEntity::getBalanceAfter)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public long getCurrentBalanceForUser(String userId) {
        AccountEntity account = accountRepository.findByUserId(userId);
        if (account == null) {
            throw new AratiriException("Account not found for user: " + userId);
        }
        return accountEntryRepository.findFirstByAccount_IdOrderByCreatedAtDescIdDesc(account.getId())
                .map(AccountEntryEntity::getBalanceAfter)
                .orElse(0L);
    }

    private long appendEntry(AccountEntity account, TransactionEntity transaction, long deltaSats, AccountEntryType entryType, String description) {
        String transactionId = transaction.getId();
        return accountEntryRepository.findFirstByAccount_IdAndTransactionIdOrderByCreatedAtDescIdDesc(account.getId(), transactionId)
                .map(AccountEntryEntity::getBalanceAfter)
                .orElseGet(() -> appendNewEntry(account, transaction, deltaSats, entryType, description));
    }

    private long appendExternalDebitSettlement(TransactionEntity transaction, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findByUserIdForUpdate(transaction.getUserId())
                .orElse(null);
        if (account == null) {
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        return appendEntry(account, transaction, -transaction.getCurrentAmount(), entryType, description);
    }

    private long appendIncomingCreditSettlement(TransactionEntity transaction, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findByUserIdForUpdate(transaction.getUserId())
                .orElse(null);
        if (account == null) {
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        return appendEntry(account, transaction, transaction.getCurrentAmount(), entryType, description);
    }

    private long appendEntry(AccountEntity account, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
        if (transactionId != null) {
            return accountEntryRepository.findFirstByAccount_IdAndTransactionIdOrderByCreatedAtDescIdDesc(account.getId(), transactionId)
                    .map(AccountEntryEntity::getBalanceAfter)
                    .orElseGet(() -> appendNewEntry(account, transactionId, deltaSats, entryType, description));
        }
        return appendNewEntry(account, transactionId, deltaSats, entryType, description);
    }

    private long appendNewEntry(AccountEntity account, TransactionEntity transaction, long deltaSats, AccountEntryType entryType, String description) {
        long previousBalance = accountEntryRepository.findFirstByAccountOrderByCreatedAtDescIdDesc(account)
                .map(AccountEntryEntity::getBalanceAfter)
                .orElse(0L);
        long newBalance = previousBalance + deltaSats;
        if (newBalance < 0) {
            throw new AratiriException("Insufficient funds for account: " + account.getId());
        }
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setAccount(account);
        entry.setTransactionId(transaction.getId());
        entry.setDeltaSats(deltaSats);
        entry.setBalanceAfter(newBalance);
        entry.setEntryType(entryType);
        entry.setDescription(description);
        accountEntryRepository.save(entry);
        webhookEventService.createAccountBalanceChangedEvent(new AccountBalanceChangedWebhookFacts(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getExternalReference(),
                transaction.getMetadata(),
                Math.abs(deltaSats),
                transaction.getReferenceId(),
                entry.getId(),
                entry.getBalanceAfter()
        ));
        return newBalance;
    }

    private long appendNewEntry(AccountEntity account, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
        long previousBalance = accountEntryRepository.findFirstByAccountOrderByCreatedAtDescIdDesc(account)
                .map(AccountEntryEntity::getBalanceAfter)
                .orElse(0L);
        long newBalance = previousBalance + deltaSats;
        if (newBalance < 0) {
            throw new AratiriException("Insufficient funds for account: " + account.getId());
        }
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setAccount(account);
        entry.setTransactionId(transactionId);
        entry.setDeltaSats(deltaSats);
        entry.setBalanceAfter(newBalance);
        entry.setEntryType(entryType);
        entry.setDescription(description);
        accountEntryRepository.save(entry);
        return newBalance;
    }
}
