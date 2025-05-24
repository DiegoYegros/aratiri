package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.Account;
import com.aratiri.aratiri.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByUser(User user);
}