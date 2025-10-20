package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByUser(UserEntity user);

    List<AccountEntity> getByUser_Id(String userId);

    String user(UserEntity user);

    AccountEntity findByUserId(String userId);

    boolean existsByAlias(String alias);

    Optional<AccountEntity> findByBitcoinAddress(String bitcoinAddress);

    Optional<AccountEntity> findByAlias(String alias);
}