package com.aratiri.infrastructure.persistence.ledger;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLedgerService {

    private final AccountRepository accountRepository;
    private final AccountEntryRepository accountEntryRepository;

    public AccountLedgerService(AccountRepository accountRepository, AccountEntryRepository accountEntryRepository) {
        this.accountRepository = accountRepository;
        this.accountEntryRepository = accountEntryRepository;
    }

    @Transactional
    public long appendEntryForUser(String userId, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findByUserId(userId);
        if (account == null) {
            throw new AratiriException("Account not found for user: " + userId);
        }
        return appendEntry(account, transactionId, deltaSats, entryType, description);
    }

    @Transactional
    public long appendEntry(String accountId, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
        AccountEntity account = accountRepository.findById(accountId)
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
        return accountEntryRepository.findFirstByAccountOrderByCreatedAtDescIdDesc(account)
                .map(AccountEntryEntity::getBalanceAfter)
                .orElse(0L);
    }

    private long appendEntry(AccountEntity account, String transactionId, long deltaSats, AccountEntryType entryType, String description) {
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
