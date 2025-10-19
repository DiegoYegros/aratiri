package com.aratiri.accounts.infrastructure.persistence;

import com.aratiri.accounts.application.port.out.AccountPersistencePort;
import com.aratiri.accounts.domain.Account;
import com.aratiri.accounts.domain.AccountUser;
import com.aratiri.entity.AccountEntity;
import com.aratiri.entity.UserEntity;
import com.aratiri.repository.AccountRepository;
import com.aratiri.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AccountRepositoryAdapter implements AccountPersistencePort {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountRepositoryAdapter(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<Account> findById(String id) {
        return accountRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByUserId(String userId) {
        AccountEntity accountEntity = accountRepository.findByUserId(userId);
        return Optional.ofNullable(accountEntity).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByAlias(String alias) {
        return accountRepository.findByAlias(alias).map(this::toDomain);
    }

    @Override
    public Account save(Account account) {
        AccountEntity entity = account.id() != null
                ? accountRepository.findById(account.id()).orElse(new AccountEntity())
                : new AccountEntity();
        entity.setBitcoinAddress(account.bitcoinAddress());
        entity.setBalance(account.balance());
        entity.setAlias(account.alias());
        UserEntity userEntity = userRepository.getReferenceById(account.user().id());
        entity.setUser(userEntity);
        AccountEntity saved = accountRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByAlias(String alias) {
        return accountRepository.existsByAlias(alias);
    }

    private Account toDomain(AccountEntity entity) {
        AccountUser user = new AccountUser(entity.getUser().getId());
        return new Account(entity.getId(), user, entity.getBalance(), entity.getBitcoinAddress(), entity.getAlias());
    }
}
