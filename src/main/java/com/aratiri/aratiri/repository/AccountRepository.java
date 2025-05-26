package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByUser(UserEntity user);

    List<AccountEntity> getByUser_Id(String userId);

    String user(UserEntity user);

    AccountEntity findByUserId(String userId);
}