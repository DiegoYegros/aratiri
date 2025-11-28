package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AccountEntryRepository extends JpaRepository<AccountEntryEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountEntryEntity> findFirstByAccountOrderByCreatedAtDescIdDesc(AccountEntity account);

    Optional<AccountEntryEntity> findFirstByAccount_IdOrderByCreatedAtDescIdDesc(String accountId);
}
