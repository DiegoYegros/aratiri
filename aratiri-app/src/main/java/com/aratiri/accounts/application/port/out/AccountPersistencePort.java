package com.aratiri.accounts.application.port.out;

import com.aratiri.accounts.domain.Account;

import java.util.Optional;

public interface AccountPersistencePort {

    Optional<Account> findById(String id);

    Optional<Account> findByUserId(String userId);

    Optional<Account> findByAlias(String alias);

    Account save(Account account);

    boolean existsByAlias(String alias);
}
